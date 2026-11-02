package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.taller.MovimientosTallerDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.MovimientoPiezaTallerRepository;
import com.drivemaster.drivemaster.repository.PiezaTallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.IvaTaller;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Movimientos del inventario de un taller: la tabla (con los filtros de la de la tienda), su Excel y PDF, los movimientos
 * manuales y la carga inicial. El taller sale siempre de la sesión: cada uno ve y mueve solo lo suyo.
 *
 * Los movimientos de un taller son pocos (cientos), así que se filtran y paginan en memoria con las mismas reglas que
 * la tabla de la tienda (TablasTiendaService.movimientos).
 */
@Service
public class MovimientosTallerService {

    public static final String ENTRADAS = "ENTRADA";
    public static final String SALIDAS = "SALIDA";
    public static final String AJUSTES = "AJUSTE";

    public static final PeticionTabla.OrdenPermitido ORDEN = PeticionTabla.OrdenPermitido.de(
            Sort.by(Sort.Order.desc("fecha")), "fecha", "fecha", "tipo", "tipo", "cantidad", "cantidad");
    private static final double COSTO_MAXIMO = 100_000_000;
    private static final int CANTIDAD_MAXIMA = 9_999;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Lo que se lee en la columna Tipo, en la pantalla y en los archivos. */
    public static final Map<String, String> CLASES = Map.of(
            MovimientoPiezaTaller.COMPRA, "Compra",
            MovimientoPiezaTaller.VENTA, "Venta en cotización",
            MovimientoPiezaTaller.CANCELACION, "Devolución por cancelación",
            MovimientoPiezaTaller.REVERSA, "Reversa de venta",
            MovimientoPiezaTaller.AJUSTE, "Ajuste por conteo",
            MovimientoPiezaTaller.CARGA_INICIAL, "Carga inicial",
            MovimientoPiezaTaller.MERMA, "Merma",
            MovimientoPiezaTaller.DEVOLUCION_TIENDA, "Devolución a la tienda",
            MovimientoPiezaTaller.DEVOLUCION_CLIENTE, "Devolución del cliente");

    private final TallerService tallerService;
    private final PiezaTallerRepository piezas;
    private final MovimientoPiezaTallerRepository movimientos;
    private final StockTallerService stock;
    private final ProductoService productos;
    private final TiendaRepository tiendas;
    private final UsuarioRepository usuarios;
    private final PrecioPublicoTaller precioPublico;
    private final ReferenciasMovimiento referencias;
    private final ExportacionMovimientos exportacion;

    public MovimientosTallerService(TallerService tallerService, PiezaTallerRepository piezas,
                                    MovimientoPiezaTallerRepository movimientos, StockTallerService stock,
                                    ProductoService productos, TiendaRepository tiendas, UsuarioRepository usuarios,
                                    PrecioPublicoTaller precioPublico, ReferenciasMovimiento referencias,
                                    ExportacionMovimientos exportacion) {
        this.tallerService = tallerService;
        this.piezas = piezas;
        this.movimientos = movimientos;
        this.stock = stock;
        this.productos = productos;
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.precioPublico = precioPublico;
        this.referencias = referencias;
        this.exportacion = exportacion;
    }

    public record Filtros(String tipo, String busqueda, RangoFechas rango) {
    }

    // ─── Tabla ────────────────────────────────────────────────────────────────

    public MovimientosTallerDTO.Tabla tabla(String correoTaller, Filtros f, PeticionTabla t) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        List<MovimientoPiezaTaller> todos = movimientos.findByTallerIdOrderByFechaDesc(taller.getId());
        Map<String, PiezaTaller> porId = piezasDe(taller);
        List<MovimientoPiezaTaller> filtrados = filtrar(todos, f, porId).stream().sorted(orden(t)).toList();

        int desde = t.paginaPara(filtrados.size()) * t.tamano();
        List<MovimientoPiezaTaller> pagina = filtrados.subList(Math.min(desde, filtrados.size()),
                Math.min(desde + t.tamano(), filtrados.size()));
        Map<String, PedidoTienda> cache = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        List<MovimientosTallerDTO.Fila> filas = pagina.stream().map(m -> fila(m, porId.get(m.getPiezaId()), cache, nombres)).toList();
        return new MovimientosTallerDTO.Tabla(t.pagina(filas, filtrados.size()), resumen(todos));
    }

    /** Excel o PDF con exactamente lo que filtra la pantalla, con el mismo formato que el de la tienda. */
    public byte[] exportar(String correoTaller, Filtros f, boolean pdf) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        Map<String, PiezaTaller> porId = piezasDe(taller);
        Map<String, PedidoTienda> cache = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        List<ExportacionMovimientos.Fila> filas = filtrar(movimientos.findByTallerIdOrderByFechaDesc(taller.getId()), f, porId).stream()
                .map(m -> {
                    MovimientosTallerDTO.Fila x = fila(m, porId.get(m.getPiezaId()), cache, nombres);
                    String motivo = x.motivo() == null ? "" : x.motivo();
                    if (x.referencia() != null) motivo += " · " + x.referencia().texto();
                    if (!x.lotes().isEmpty()) motivo += " · " + String.join("; ", x.lotes());
                    return new ExportacionMovimientos.Fila(x.fecha(), x.piezaNombre(), x.piezaReferencia(),
                            CLASES.getOrDefault(x.clase(), x.clase()), x.cantidad(), x.stockAnterior(), x.stockNuevo(), motivo, x.usuario());
                }).toList();
        if (!pdf) return exportacion.excel(filas);
        String resumen = (f.tipo() == null || f.tipo().isBlank() ? "Todos los movimientos"
                : ENTRADAS.equals(f.tipo()) ? "Entradas" : SALIDAS.equals(f.tipo()) ? "Salidas" : "Ajustes")
                + (f.busqueda() != null && !f.busqueda().isBlank() ? " · pieza “" + f.busqueda().trim() + "”" : "");
        return exportacion.pdf(filas, taller.getNombre(), resumen);
    }

    private Map<String, PiezaTaller> piezasDe(Taller taller) {
        return piezas.findByTallerIdOrderByNombreAsc(taller.getId()).stream()
                .collect(Collectors.toMap(PiezaTaller::getId, Function.identity(), (a, b) -> a));
    }

    static List<MovimientoPiezaTaller> filtrar(List<MovimientoPiezaTaller> lista, Filtros f, Map<String, PiezaTaller> piezas) {
        String tipo = f.tipo() == null ? "" : f.tipo().trim().toUpperCase(Locale.ROOT);
        if (!tipo.isEmpty() && !Set.of(ENTRADAS, SALIDAS, AJUSTES).contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo desconocido: " + f.tipo());
        }
        String q = normalizar(f.busqueda());
        RangoFechas rango = f.rango() != null ? f.rango() : RangoFechas.SIN_RANGO;
        return lista.stream()
                .filter(m -> tipo.isEmpty() || grupo(m).equals(tipo))
                .filter(m -> rango.contiene(m.getFecha()))
                .filter(m -> {
                    if (q.isEmpty()) return true;
                    PiezaTaller p = piezas.get(m.getPiezaId());
                    return normalizar(m.getPiezaNombre()).contains(q)
                            || (p != null && (normalizar(p.getNombre()).contains(q) || normalizar(p.getReferencia()).contains(q)));
                })
                .toList();
    }

    /** El filtro de la pantalla: una reversa o devolución suma, así que es una entrada. */
    static String grupo(MovimientoPiezaTaller m) {
        if (MovimientoPiezaTaller.AJUSTE.equals(m.getTipo())) return AJUSTES;
        if (MovimientoPiezaTaller.SALIDA.equals(m.getTipo())) return SALIDAS;
        return ENTRADAS;
    }

    private static Comparator<MovimientoPiezaTaller> orden(PeticionTabla t) {
        Comparator<MovimientoPiezaTaller> porFecha = Comparator.comparing(MovimientoPiezaTaller::getFecha,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        if (t.campoOrden() == null) return porFecha.reversed();
        Comparator<MovimientoPiezaTaller> c = switch (t.campoOrden()) {
            case "tipo" -> Comparator.comparing(m -> CLASES.getOrDefault(MovimientoPiezaTaller.claseDe(m), ""));
            case "cantidad" -> Comparator.comparingInt(MovimientoPiezaTaller::getCantidad);
            default -> porFecha;
        };
        c = t.ascendente() ? c : c.reversed();
        return c.thenComparing(porFecha.reversed());
    }

    private static MovimientosTallerDTO.Resumen resumen(List<MovimientoPiezaTaller> todos) {
        long entradas = 0, salidas = 0, ajustes = 0;
        for (MovimientoPiezaTaller m : todos) {
            switch (grupo(m)) {
                case SALIDAS -> salidas += m.getCantidad();
                case AJUSTES -> ajustes++;
                default -> entradas += m.getCantidad();
            }
        }
        return new MovimientosTallerDTO.Resumen(todos.size(), entradas, salidas, ajustes);
    }

    private MovimientosTallerDTO.Fila fila(MovimientoPiezaTaller m, PiezaTaller p, Map<String, PedidoTienda> cache,
                                          Map<String, String> nombres) {
        Map<String, PiezaTaller.Lote> lotes = p == null ? Map.of() : p.getLotes().stream()
                .collect(Collectors.toMap(PiezaTaller.Lote::getId, Function.identity(), (a, b) -> a));
        List<String> textoLotes = (m.getLotes() != null ? m.getLotes() : List.<MovimientoPiezaTaller.ConsumoLote>of()).stream()
                .map(c -> c.getCantidad() + " del " + textoLote(lotes.get(c.getLoteId()))).toList();
        return new MovimientosTallerDTO.Fila(m.getId(), m.getPiezaId(), p != null ? p.getNombre() : m.getPiezaNombre(),
                p != null ? p.getReferencia() : null, p != null ? p.getImagenUrl() : null, m.getFecha(), m.getTipo(),
                MovimientoPiezaTaller.claseDe(m), m.getCantidad(), m.getStockAnterior(), m.getStockNuevo(), textoLotes,
                m.getMotivo(), referencias.deTaller(m, cache),
                nombres.computeIfAbsent(m.getUsuarioId() == null ? "" : m.getUsuarioId(), this::usuario));
    }

    /** "lote del 12/10/2026 · La Vaquita"; la carga inicial y lo que sobró de un conteo se nombran así. */
    static String textoLote(PiezaTaller.Lote l) {
        if (l == null) return "lote que ya no existe";
        String fecha = l.getFechaEntrada() != null ? " del " + l.getFechaEntrada().format(DIA) : "";
        String origen = l.getId() != null && l.getId().startsWith("CARGA-") ? "carga inicial"
                : l.getId() != null && l.getId().startsWith("MANUAL-") ? "ajuste"
                : l.getTiendaNombre();
        return "lote" + fecha + (origen != null ? " · " + origen : "");
    }

    /** Las compras guardan el id del cliente y los manuales el correo: se muestra el correo. */
    private String usuario(String usuarioId) {
        if (usuarioId.isBlank()) return "DriveMaster";
        if (usuarioId.contains("@")) return usuarioId;
        return usuarios.findById(usuarioId).map(Usuario::getCorreo).orElse("—");
    }

    // ─── Registrar ───────────────────────────────────────────────────────────

    /** Un movimiento manual o una carga inicial. Quién: el correo de la sesión; cuándo: ahora. */
    public MovimientoPiezaTaller registrar(String correoTaller, MovimientosTallerDTO.Registro r) {
        Taller taller = operativo(correoTaller);
        if (r == null || r.clase() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige el tipo de movimiento");
        if (r.cantidad() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe la cantidad");
        if (r.cantidad() > CANTIDAD_MAXIMA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad puede ser hasta " + CANTIDAD_MAXIMA);
        }
        if (MovimientoPiezaTaller.CARGA_INICIAL.equals(r.clase())) return cargaInicial(taller, correoTaller, r);
        if (r.piezaId() == null || r.piezaId().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige la pieza");
        return stock.registrarManual(new StockTallerService.Manual(taller.getId(), r.piezaId(), r.clase(), r.cantidad(),
                r.motivo(), correoTaller));
    }

    /**
     * Piezas que el taller ya tenía antes de DriveMaster: entra un lote nuevo sin compra, con el costo que dice el taller
     * (sin IVA si es responsable, con IVA si no lo es: la misma regla D2). La pieza se identifica con un producto del
     * catálogo, así el precio público y el tope funcionan igual que con una compra. Si la pieza es nueva, su precio
     * inicial es el tope.
     */
    MovimientoPiezaTaller cargaInicial(Taller taller, String correo, MovimientosTallerDTO.Registro r) {
        String motivo = StockTallerService.motivoObligatorio(r.motivo());
        if (r.cantidad() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que 0");
        if (r.costo() == null || r.costo() <= 0 || r.costo() > COSTO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe lo que te costó cada unidad");
        }
        if (r.productoId() == null || r.productoId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige la pieza del catálogo");
        }
        Producto producto;
        try {
            producto = productos.obtenerPorId(r.productoId());
        } catch (RuntimeException e) {
            producto = null;
        }
        if (producto == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa pieza ya no está en el catálogo");
        Tienda tienda = producto.getTiendaId() != null ? tiendas.findById(producto.getTiendaId()).orElse(null) : null;
        Double publico = producto.getPrecioVenta() != null && producto.getPrecioVenta() > 0 ? producto.getPrecioVenta() : null;

        PiezaTaller datos = PiezaTaller.builder().clave(BuscadorRepuestosService.clavePieza(producto)).nombre(producto.getNombre())
                .marca(producto.getMarca()).referencia(producto.getReferencia()).categoria(producto.getCategoria())
                .imagenUrl(producto.getImagenUrl()).modelosCompatibles(producto.getModelosCompatibles())
                .compatibilidadUniversal(producto.getCompatibilidadUniversal())
                .precioVenta(publico != null ? precioPublico.tope(publico, taller) : null).build();
        String referencia = "CARGA-" + UUID.randomUUID();
        PiezaTaller.Lote lote = PiezaTaller.Lote.builder().id(referencia).productoId(producto.getId())
                .tiendaId(producto.getTiendaId()).tiendaNombre(tienda != null ? tienda.getNombre() : null)
                .cantidad(r.cantidad()).disponible(r.cantidad()).costoUnitario((double) Math.round(r.costo()))
                .ivaEnCosto(!IvaTaller.esResponsable(taller)).precioPublico(publico).fechaEntrada(LocalDateTime.now()).build();
        PiezaTaller pieza = stock.entrar(new StockTallerService.Entrada(taller.getId(), datos, lote, correo, referencia, motivo,
                MovimientoPiezaTaller.CARGA_INICIAL));
        return movimientos.findById(MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.ENTRADA, pieza.getId(), referencia)).orElse(null);
    }

    /** Mínimo para la alerta de stock bajo; null la apaga. */
    public PiezaTaller cambiarMinimo(String correoTaller, String piezaId, Integer minimo) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        return stock.cambiarMinimo(taller.getId(), piezaId, minimo);
    }

    private Taller operativo(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion()) || !Boolean.TRUE.equals(taller.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller no está habilitado para operar");
        }
        return taller;
    }

    private static String normalizar(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
