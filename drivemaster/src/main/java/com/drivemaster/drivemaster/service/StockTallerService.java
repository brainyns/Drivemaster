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

    /** Lo que entra: los datos de la pieza (solo se usan si es nueva) y el lote. */
    public record Entrada(String tallerId, PiezaTaller datos, PiezaTaller.Lote lote, String usuarioId) {
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
            if (pieza.getPrecioVenta() == null) pieza.setPrecioVenta(sugerido(lote.getCostoUnitario()));
            PiezaTaller guardada = piezas.save(pieza);
            registrarEntrada(guardada, lote, anterior, e.usuarioId());
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

    private void registrarEntrada(PiezaTaller pieza, PiezaTaller.Lote lote, int anterior, String usuarioId) {
        String referencia = "PEDIDO-" + lote.getPedidoId() + "-" + lote.getProductoId();
        movimientos.save(MovimientoPiezaTaller.builder()
                .id(MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.ENTRADA, pieza.getId(), referencia))
                .tallerId(pieza.getTallerId()).piezaId(pieza.getId()).piezaNombre(pieza.getNombre())
                .tipo(MovimientoPiezaTaller.ENTRADA).cantidad(lote.getDisponible())
                .stockAnterior(anterior).stockNuevo(pieza.getStock()).referencia(referencia).pedidoId(lote.getPedidoId())
                .motivo("El taller confirmó que llegó el pedido")
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
            PiezaTaller guardada = piezas.save(pieza);
            MovimientoPiezaTaller salida = MovimientoPiezaTaller.builder().id(id).tallerId(guardada.getTallerId())
                    .piezaId(guardada.getId()).piezaNombre(guardada.getNombre()).tipo(MovimientoPiezaTaller.SALIDA)
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
                return piezas.save(actual);
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
        int revertidas = 0;
        for (MovimientoPiezaTaller salida : movimientos.findByReferenciaAndTipo(referencia, MovimientoPiezaTaller.SALIDA)) {
            if (revertirSalida(salida, motivo, usuarioId)) revertidas++;
        }
        return revertidas;
    }

    private boolean revertirSalida(MovimientoPiezaTaller salida, String motivo, String usuarioId) {
        String id = MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.REVERSA, salida.getPiezaId(), salida.getReferencia());
        MovimientoPiezaTaller reversa = MovimientoPiezaTaller.builder().id(id).tallerId(salida.getTallerId())
                .piezaId(salida.getPiezaId()).piezaNombre(salida.getPiezaNombre()).tipo(MovimientoPiezaTaller.REVERSA)
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
                return piezas.save(actual);
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
