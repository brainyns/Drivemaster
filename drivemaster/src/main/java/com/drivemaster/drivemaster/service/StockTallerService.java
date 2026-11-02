package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.repository.MovimientoPiezaTallerRepository;
import com.drivemaster.drivemaster.repository.PiezaTallerRepository;
import com.drivemaster.drivemaster.util.Dinero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Núcleo del inventario de los talleres: entradas por lote, salidas FIFO y reversas al lote de origen.
 *
 * No depende de Producto ni de los servicios de las tiendas: vender una pieza del taller no puede mover el stock de una
 * tienda. Cada escritura de una pieza lleva su version: si otra venta ganó, se repite con el stock nuevo.
 */
@Service
public class StockTallerService {

    private static final Logger log = LoggerFactory.getLogger(StockTallerService.class);
    private static final int MAX_INTENTOS = 8;

    private final PiezaTallerRepository piezas;
    private final MovimientoPiezaTallerRepository movimientos;

    public StockTallerService(PiezaTallerRepository piezas, MovimientoPiezaTallerRepository movimientos) {
        this.piezas = piezas;
        this.movimientos = movimientos;
    }

    /**
     * Lo que entra: los datos de la pieza (solo se usan si es nueva) y el lote. referencia, motivo y clase son opcionales:
     * sin ellos es la llegada de un pedido (PEDIDO-<pedido>-<producto>, clase COMPRA).
     */
    public record Entrada(String tallerId, PiezaTaller datos, PiezaTaller.Lote lote, String usuarioId,
                          String referencia, String motivo, String clase) {
        public Entrada(String tallerId, PiezaTaller datos, PiezaTaller.Lote lote, String usuarioId) {
            this(tallerId, datos, lote, usuarioId, null, null, null);
        }
    }

    /**
     * Un movimiento que registra el taller a mano. clase: AJUSTE (cantidad = stock contado), MERMA o DEVOLUCION_TIENDA
     * (salen) y DEVOLUCION_CLIENTE (entra). Ninguno mueve dinero.
     */
    public record Manual(String tallerId, String piezaId, String clase, int cantidad, String motivo, String usuarioId) {
    }

    /** Avisa al taller que una pieza llegó a su mínimo. Lo implementa AvisosSolicitudServicio (campanita). */
    public interface AvisoStockBajo {
        void stockBajo(PiezaTaller pieza);
    }

    private AvisoStockBajo aviso;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAviso(AvisoStockBajo aviso) {
        this.aviso = aviso;
    }

    /** Lo que se vende: de qué pago y orden, a cuánto cada unidad. */
    public record Venta(String tallerId, String piezaId, int cantidad, String referencia, double precioUnitario,
                        String ordenId, String cotizacionId, Long pagoId, String usuarioId) {
    }

    /** Suma el lote a la pieza, creándola si es la primera vez. Repetir el mismo lote no suma otra vez. */
    public PiezaTaller entrar(Entrada e) {
        String piezaId = PiezaTaller.idDe(e.tallerId(), e.datos().getClave());
        PiezaTaller.Lote lote = e.lote();
        return reintentando(() -> {
            PiezaTaller pieza = piezas.findById(piezaId).orElse(null);
            if (pieza == null) pieza = nueva(piezaId, e);
            else if (pieza.getLotes().stream().anyMatch(l -> lote.getId().equals(l.getId()))) return pieza;
            int anterior = pieza.getStock();
            pieza.getLotes().add(lote);
            pieza.setStock(anterior + lote.getDisponible());
            // Precio inicial: el que propone quien llama (el tope por precio público); sin él, el recargo sugerido sobre el costo
            if (pieza.getPrecioVenta() == null) {
                pieza.setPrecioVenta(e.datos().getPrecioVenta() != null ? e.datos().getPrecioVenta() : sugerido(lote.getCostoUnitario()));
            }
            PiezaTaller guardada = guardar(pieza);
            registrarEntrada(guardada, e, anterior);
            return guardada;
        });
    }

    private static PiezaTaller nueva(String piezaId, Entrada e) {
        PiezaTaller d = e.datos();
        return PiezaTaller.builder().id(piezaId).tallerId(e.tallerId()).clave(d.getClave()).nombre(d.getNombre())
                .marca(d.getMarca()).referencia(d.getReferencia()).categoria(d.getCategoria()).imagenUrl(d.getImagenUrl())
                .modelosCompatibles(d.getModelosCompatibles() != null ? d.getModelosCompatibles() : new ArrayList<>())
                .compatibilidadUniversal(d.getCompatibilidadUniversal()).lotes(new ArrayList<>())
                .fechaCreacion(LocalDateTime.now()).build();
    }

    private void registrarEntrada(PiezaTaller pieza, Entrada e, int anterior) {
        PiezaTaller.Lote lote = e.lote();
        String usuarioId = e.usuarioId();
        String referencia = e.referencia() != null ? e.referencia() : "PEDIDO-" + lote.getPedidoId() + "-" + lote.getProductoId();
        movimientos.save(MovimientoPiezaTaller.builder()
                .id(MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.ENTRADA, pieza.getId(), referencia))
                .tallerId(pieza.getTallerId()).piezaId(pieza.getId()).piezaNombre(pieza.getNombre())
                .tipo(MovimientoPiezaTaller.ENTRADA).clase(e.clase() != null ? e.clase() : MovimientoPiezaTaller.COMPRA).cantidad(lote.getDisponible())
                .stockAnterior(anterior).stockNuevo(pieza.getStock()).referencia(referencia).pedidoId(lote.getPedidoId())
                .motivo(e.motivo() != null ? e.motivo() : "El taller confirmó que llegó el pedido")
                .lotes(List.of(MovimientoPiezaTaller.ConsumoLote.builder().loteId(lote.getId())
                        .cantidad(lote.getDisponible()).costoUnitario(lote.getCostoUnitario()).build()))
                .fecha(LocalDateTime.now()).usuarioId(usuarioId).build());
    }

    /** Stock disponible de una pieza de este taller; 0 si no existe o es de otro. */
    public int disponible(String tallerId, String piezaId) {
        return piezas.findById(piezaId).filter(p -> tallerId != null && tallerId.equals(p.getTallerId()))
                .map(PiezaTaller::getStock).orElse(0);
    }

    /**
     * Saca unidades del lote más antiguo con disponible y deja el rastro de lote y costo. Si no alcanza lanza
     * StockInsuficienteException sin tocar nada. Repetir la misma venta devuelve su salida sin descontar otra vez.
     */
    public MovimientoPiezaTaller descontar(Venta v) {
        String id = MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.SALIDA, v.piezaId(), v.referencia());
        return reintentando(() -> {
            MovimientoPiezaTaller previa = movimientos.findById(id).orElse(null);
            if (previa != null) {
                log.warn("Venta {}: la salida de {} ya estaba registrada, no se descuenta otra vez", v.referencia(), v.piezaId());
                return previa;
            }
            PiezaTaller pieza = piezas.findById(v.piezaId()).filter(p -> v.tallerId().equals(p.getTallerId()))
                    .orElseThrow(() -> new StockInsuficienteException(v.piezaId(), 0));
            if (pieza.getStock() < v.cantidad()) throw new StockInsuficienteException(pieza.getNombre(), pieza.getStock());

            int anterior = pieza.getStock();
            List<MovimientoPiezaTaller.ConsumoLote> consumos = consumirFifo(pieza, v.cantidad());
            pieza.setStock(anterior - v.cantidad());
            PiezaTaller guardada = guardar(pieza);
            MovimientoPiezaTaller salida = MovimientoPiezaTaller.builder().id(id).tallerId(guardada.getTallerId())
                    .piezaId(guardada.getId()).piezaNombre(guardada.getNombre()).tipo(MovimientoPiezaTaller.SALIDA).clase(MovimientoPiezaTaller.VENTA)
                    .cantidad(v.cantidad()).stockAnterior(anterior).stockNuevo(guardada.getStock()).referencia(v.referencia())
                    .motivo("Venta en cotización").ordenId(v.ordenId()).cotizacionId(v.cotizacionId()).pagoId(v.pagoId())
                    .precioVentaUnitario(v.precioUnitario()).lotes(consumos).fecha(LocalDateTime.now()).usuarioId(v.usuarioId()).build();
            return registrarSalida(guardada, salida, consumos);
        });
    }

    /** Sin el rastro la venta no se podría revertir: si no se puede guardar, las unidades vuelven y falla la venta. */
    private MovimientoPiezaTaller registrarSalida(PiezaTaller pieza, MovimientoPiezaTaller salida,
                                                  List<MovimientoPiezaTaller.ConsumoLote> consumos) {
        try {
            return movimientos.insert(salida);
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la salida {}: se devuelven sus unidades", salida.getId(), e);
            reintentando(() -> {
                PiezaTaller actual = piezas.findById(pieza.getId()).orElseThrow();
                devolverALotes(actual, consumos);
                return guardar(actual);
            });
            throw e;
        }
    }

    private static List<MovimientoPiezaTaller.ConsumoLote> consumirFifo(PiezaTaller pieza, int cantidad) {
        List<PiezaTaller.Lote> enOrden = pieza.getLotes().stream()
                .sorted(Comparator.comparing(PiezaTaller.Lote::getFechaEntrada, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        List<MovimientoPiezaTaller.ConsumoLote> consumos = new ArrayList<>();
        int falta = cantidad;
        for (PiezaTaller.Lote lote : enOrden) {
            if (falta == 0) break;
            int saca = Math.min(falta, lote.getDisponible());
            if (saca == 0) continue;
            lote.setDisponible(lote.getDisponible() - saca);
            falta -= saca;
            consumos.add(MovimientoPiezaTaller.ConsumoLote.builder().loteId(lote.getId()).cantidad(saca)
                    .costoUnitario(lote.getCostoUnitario()).build());
        }
        if (falta > 0) throw new IllegalStateException("La pieza " + pieza.getId() + " suma menos en sus lotes que en su stock");
        return consumos;
    }

    /**
     * Devuelve a sus lotes de origen todo lo que salió con esta referencia. La reversa se anota antes de sumar: dos
     * llamadas a la vez no pueden devolver dos veces. Devuelve cuántas piezas revirtió.
     */
    public int revertir(String referencia, String motivo, String usuarioId) {
        return revertir(referencia, motivo, usuarioId, MovimientoPiezaTaller.REVERSA);
    }

    /** clase: REVERSA (pago no aplicado, reembolso) o CANCELACION (la orden la canceló DriveMaster). */
    public int revertir(String referencia, String motivo, String usuarioId, String clase) {
        int revertidas = 0;
        for (MovimientoPiezaTaller salida : movimientos.findByReferenciaAndTipo(referencia, MovimientoPiezaTaller.SALIDA)) {
            if (revertirSalida(salida, motivo, usuarioId, clase)) revertidas++;
        }
        return revertidas;
    }

    private boolean revertirSalida(MovimientoPiezaTaller salida, String motivo, String usuarioId, String clase) {
        String id = MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.REVERSA, salida.getPiezaId(), salida.getReferencia());
        MovimientoPiezaTaller reversa = MovimientoPiezaTaller.builder().id(id).tallerId(salida.getTallerId())
                .piezaId(salida.getPiezaId()).piezaNombre(salida.getPiezaNombre()).tipo(MovimientoPiezaTaller.REVERSA).clase(clase)
                .cantidad(salida.getCantidad()).referencia(salida.getReferencia()).motivo(motivo).ordenId(salida.getOrdenId())
                .cotizacionId(salida.getCotizacionId()).pagoId(salida.getPagoId()).lotes(salida.getLotes())
                .fecha(LocalDateTime.now()).usuarioId(usuarioId).build();
        try {
            movimientos.insert(reversa);
        } catch (DuplicateKeyException e) {
            log.warn("La salida {} ya estaba revertida", salida.getId());
            return false;
        }
        try {
            PiezaTaller pieza = reintentando(() -> {
                PiezaTaller actual = piezas.findById(salida.getPiezaId())
                        .orElseThrow(() -> new IllegalStateException("No existe la pieza " + salida.getPiezaId()));
                reversa.setStockAnterior(actual.getStock());
                devolverALotes(actual, salida.getLotes());
                return guardar(actual);
            });
            reversa.setStockNuevo(pieza.getStock());
            movimientos.save(reversa);
            return true;
        } catch (RuntimeException e) {
            // Sin esto la reversa quedaría anotada sin haberse hecho y nadie podría repetirla
            movimientos.deleteById(id);
            throw e;
        }
    }

    private static void devolverALotes(PiezaTaller pieza, List<MovimientoPiezaTaller.ConsumoLote> consumos) {
        for (MovimientoPiezaTaller.ConsumoLote c : consumos) {
            PiezaTaller.Lote lote = pieza.getLotes().stream().filter(l -> c.getLoteId().equals(l.getId())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No existe el lote " + c.getLoteId()));
            lote.setDisponible(lote.getDisponible() + c.getCantidad());
            pieza.setStock(pieza.getStock() + c.getCantidad());
        }
    }

    /** Precio de venta nuevo, sin pisar una venta que ocurra al mismo tiempo. */
    public PiezaTaller cambiarPrecio(String tallerId, String piezaId, double precio) {
        return reintentando(() -> {
            PiezaTaller pieza = piezas.findById(piezaId).filter(p -> tallerId.equals(p.getTallerId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pieza no encontrada"));
            pieza.setPrecioVenta(precio);
            return guardar(pieza);
        });
    }

    // ─── Movimientos manuales y alerta de stock bajo ───────────────────────────

    private static final Set<String> CLASES_MANUALES = Set.of(MovimientoPiezaTaller.AJUSTE, MovimientoPiezaTaller.MERMA,
            MovimientoPiezaTaller.DEVOLUCION_TIENDA, MovimientoPiezaTaller.DEVOLUCION_CLIENTE);
    static final int MAX_MOTIVO = 200;

    /**
     * Ajuste por conteo, merma o devolución. Lo que sale, sale por FIFO como una venta; lo que entra vuelve al lote más
     * reciente que tenga espacio (así conserva su costo) y, si sobra, a un lote nuevo con el costo del último. El stock
     * nunca queda negativo y el motivo es obligatorio: queda en el historial con quién y cuándo.
     */
    public MovimientoPiezaTaller registrarManual(Manual m) {
        String motivo = motivoObligatorio(m.motivo());
        if (!CLASES_MANUALES.contains(m.clase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de movimiento desconocido");
        }
        boolean ajuste = MovimientoPiezaTaller.AJUSTE.equals(m.clase());
        if (m.cantidad() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad no puede ser negativa");
        if (!ajuste && m.cantidad() == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que 0");
        String referencia = "MANUAL-" + UUID.randomUUID();
        return reintentando(() -> {
            PiezaTaller pieza = piezas.findById(m.piezaId()).filter(p -> m.tallerId().equals(p.getTallerId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa pieza no está en tu inventario"));
            int anterior = pieza.getStock();
            boolean sale = MovimientoPiezaTaller.MERMA.equals(m.clase()) || MovimientoPiezaTaller.DEVOLUCION_TIENDA.equals(m.clase());
            int delta = ajuste ? m.cantidad() - anterior : sale ? -m.cantidad() : m.cantidad();
            if (ajuste && delta == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El conteo coincide con el stock: no hay nada que ajustar");
            }
            if (anterior + delta < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "De \"" + pieza.getNombre() + "\" solo tienes " + anterior
                        + " en tu inventario: no puedes sacar " + (-delta));
            }
            List<MovimientoPiezaTaller.ConsumoLote> lotes = delta < 0 ? consumirFifo(pieza, -delta) : reponer(pieza, delta, referencia);
            pieza.setStock(anterior + delta);
            PiezaTaller guardada = guardar(pieza);
            String tipo = ajuste ? MovimientoPiezaTaller.AJUSTE : delta < 0 ? MovimientoPiezaTaller.SALIDA : MovimientoPiezaTaller.ENTRADA;
            MovimientoPiezaTaller mov = MovimientoPiezaTaller.builder().id(MovimientoPiezaTaller.idDe(tipo, guardada.getId(), referencia))
                    .tallerId(guardada.getTallerId()).piezaId(guardada.getId()).piezaNombre(guardada.getNombre()).tipo(tipo)
                    .clase(m.clase()).cantidad(Math.abs(delta)).stockAnterior(anterior).stockNuevo(guardada.getStock())
                    .referencia(referencia).motivo(motivo).lotes(lotes).fecha(LocalDateTime.now()).usuarioId(m.usuarioId()).build();
            try {
                return movimientos.insert(mov);
            } catch (RuntimeException e) {
                log.error("No se pudo registrar el movimiento {}: se deshace", mov.getId(), e);
                reintentando(() -> {
                    PiezaTaller actual = piezas.findById(guardada.getId()).orElseThrow();
                    if (delta < 0) devolverALotes(actual, lotes);
                    else quitarDeLotes(actual, lotes);
                    return guardar(actual);
                });
                throw e;
            }
        });
    }

    /** Suma unidades a los lotes del más reciente al más antiguo, hasta su cantidad original; lo que sobra, a un lote nuevo. */
    private static List<MovimientoPiezaTaller.ConsumoLote> reponer(PiezaTaller pieza, int cantidad, String referencia) {
        List<PiezaTaller.Lote> recientes = pieza.getLotes().stream()
                .sorted(Comparator.comparing(PiezaTaller.Lote::getFechaEntrada, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
        List<MovimientoPiezaTaller.ConsumoLote> repuestos = new ArrayList<>();
        int falta = cantidad;
        for (PiezaTaller.Lote lote : recientes) {
            if (falta == 0) break;
            int cabe = Math.min(falta, lote.getCantidad() - lote.getDisponible());
            if (cabe <= 0) continue;
            lote.setDisponible(lote.getDisponible() + cabe);
            falta -= cabe;
            repuestos.add(MovimientoPiezaTaller.ConsumoLote.builder().loteId(lote.getId()).cantidad(cabe).costoUnitario(lote.getCostoUnitario()).build());
        }
        if (falta > 0) {
            PiezaTaller.Lote ultimo = recientes.isEmpty() ? null : recientes.get(0);
            PiezaTaller.Lote nuevo = PiezaTaller.Lote.builder().id(referencia).cantidad(falta).disponible(falta)
                    .costoUnitario(ultimo != null ? ultimo.getCostoUnitario() : null)
                    .ivaEnCosto(ultimo != null ? ultimo.getIvaEnCosto() : null)
                    .productoId(ultimo != null ? ultimo.getProductoId() : null)
                    .tiendaId(ultimo != null ? ultimo.getTiendaId() : null)
                    .tiendaNombre(ultimo != null ? ultimo.getTiendaNombre() : null)
                    .precioPublico(ultimo != null ? ultimo.getPrecioPublico() : null)
                    .fechaEntrada(LocalDateTime.now()).build();
            pieza.getLotes().add(nuevo);
            repuestos.add(MovimientoPiezaTaller.ConsumoLote.builder().loteId(nuevo.getId()).cantidad(falta).costoUnitario(nuevo.getCostoUnitario()).build());
        }
        return repuestos;
    }

    private static void quitarDeLotes(PiezaTaller pieza, List<MovimientoPiezaTaller.ConsumoLote> repuestos) {
        for (MovimientoPiezaTaller.ConsumoLote c : repuestos) {
            pieza.getLotes().stream().filter(l -> c.getLoteId().equals(l.getId())).findFirst().ifPresent(l -> {
                l.setDisponible(l.getDisponible() - c.getCantidad());
                pieza.setStock(pieza.getStock() - c.getCantidad());
            });
        }
    }

    static String motivoObligatorio(String motivo) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el motivo: queda en el historial");
        if (m.length() > MAX_MOTIVO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo puede tener hasta " + MAX_MOTIVO + " caracteres");
        }
        return m;
    }

    /** Mínimo de una pieza para la alerta de stock bajo; null la apaga. Si ya está en el mínimo o debajo, avisa de una vez. */
    public PiezaTaller cambiarMinimo(String tallerId, String piezaId, Integer minimo) {
        if (minimo != null && (minimo < 0 || minimo > 9_999)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mínimo va de 0 a 9.999 unidades");
        }
        return reintentando(() -> {
            PiezaTaller pieza = piezas.findById(piezaId).filter(p -> tallerId.equals(p.getTallerId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pieza no encontrada"));
            pieza.setStockMinimo(minimo);
            return guardar(pieza);
        });
    }

    /**
     * Guarda la pieza y avisa una sola vez cuando llega a su mínimo: la marca viaja en el mismo documento (con su version),
     * así dos movimientos a la vez no avisan dos veces. Al volver a subir sobre el mínimo, la marca se borra.
     */
    private PiezaTaller guardar(PiezaTaller pieza) {
        boolean avisar = marcarAlerta(pieza);
        PiezaTaller guardada = piezas.save(pieza);
        if (avisar && aviso != null) {
            try {
                aviso.stockBajo(guardada);
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar el stock bajo de {}: {}", guardada.getId(), e.getMessage());
            }
        }
        return guardada;
    }

    /** true si hay que avisar ahora. */
    static boolean marcarAlerta(PiezaTaller p) {
        boolean bajo = p.getStockMinimo() != null && p.getStock() <= p.getStockMinimo();
        boolean avisada = Boolean.TRUE.equals(p.getAvisoStockBajo());
        if (bajo && !avisada) {
            p.setAvisoStockBajo(true);
            return true;
        }
        if (!bajo && avisada) p.setAvisoStockBajo(false);
        return false;
    }

    /** Precio propuesto para un costo: el recargo sugerido, en pesos enteros. */
    static double sugerido(Double costo) {
        BigDecimal base = BigDecimal.valueOf(costo != null ? costo : 0);
        return Dinero.aDouble(Dinero.pesos(base.multiply(BigDecimal.valueOf(100 + PiezaTaller.RECARGO_SUGERIDO_PCT))
                .divide(BigDecimal.valueOf(100))));
    }

    private static <T> T reintentando(Supplier<T> operacion) {
        for (int i = 1; ; i++) {
            try {
                return operacion.get();
            } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
                if (i >= MAX_INTENTOS) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El inventario está cambiando: vuelve a intentar");
                }
            }
        }
    }
}
