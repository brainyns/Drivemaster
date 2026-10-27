package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Cliente;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Entrega;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Evento;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Linea;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.LineaReparto;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Mecanico;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Negocio;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Parte;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Pedido;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.PedidoResumen;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Problema;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.ProductoResumen;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.SolicitudResumen;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Totales;
import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Vehiculo;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Vistas de detalle del SUPERADMIN: un pedido (con su línea de tiempo, el pago en Wompi y el reparto), un cliente
 * (vehículos, pedidos y solicitudes) y un negocio (datos, productos o servicios, pedidos y totales). Solo lectura.
 */
@Service
public class PlataformaDetalleService {

    static final int RECIENTES = 10;

    // Pedidos que no se cobran al final: no suman en lo vendido
    private static final Set<String> SIN_VENTA = Set.of(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO);

    private final MongoTemplate mongo;
    private final PagoRepository pagos;
    private final RepartoPagoRepository repartos;
    private final ReembolsoRepository reembolsos;

    public PlataformaDetalleService(MongoTemplate mongo, PagoRepository pagos, RepartoPagoRepository repartos,
                                    ReembolsoRepository reembolsos) {
        this.mongo = mongo;
        this.pagos = pagos;
        this.repartos = repartos;
        this.reembolsos = reembolsos;
    }

    // ─── Pedido ─────────────────────────────────────────────────────────────

    public Pedido pedido(String id) {
        return pedido(id, LocalDateTime.now());
    }

    Pedido pedido(String id, LocalDateTime ahora) {
        PedidoTienda p = existe(mongo.findById(id, PedidoTienda.class), "Pedido no encontrado");
        Tienda tienda = p.getTiendaId() == null ? null : mongo.findById(p.getTiendaId(), Tienda.class);
        Usuario cliente = p.getClienteId() == null ? null : mongo.findById(p.getClienteId(), Usuario.class);
        Parte parteTienda = new Parte(p.getTiendaId(), p.getTiendaNombre(), null,
                tienda == null ? null : tienda.getTelefono(), p.getTiendaCiudad() != null ? p.getTiendaCiudad()
                : tienda == null ? null : tienda.getCiudad());
        Parte parteCliente = p.getClienteId() == null && cliente == null ? null
                : new Parte(p.getClienteId(), cliente != null ? cliente.getNombre() : p.getClienteNombre(),
                        cliente == null ? null : PlataformaConsultasService.enmascarar(cliente.getCorreo()),
                        cliente == null ? null : cliente.getTelefono(), cliente == null ? null : cliente.getCiudad());
        Parte parteTaller = p.getTallerId() == null && p.getTallerNombre() == null ? null
                : new Parte(p.getTallerId(), p.getTallerNombre(), null, p.getTallerTelefono(), p.getTallerCiudad());

        List<Linea> lineas = p.getItems() == null ? List.of() : p.getItems().stream()
                .map(i -> new Linea(i.getProductoNombre() != null ? i.getProductoNombre() : i.getDescripcion(), i.getMarca(),
                        i.getReferencia(), i.getDisponibilidad(), i.getCantidad() == null ? 0 : i.getCantidad(),
                        n(i.getPrecioUnitario()), n(i.getSubtotal())))
                .toList();

        PagoEntity pago = p.getPagoId() == null ? null : pagos.findById(p.getPagoId()).orElse(null);
        int pedidosDelPago = p.getPagoId() == null ? 0
                : (int) mongo.count(Query.query(Criteria.where("pagoId").is(p.getPagoId())), PedidoTienda.class);
        var datosPago = pago == null ? null : new com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Pago(
                pago.getId(), pago.getReferencia(), pago.getTransactionId(), pago.getMetodo(), pago.getEstado(),
                n(pago.getMonto()), pago.getFechaPago(), pago.getConcepto(), pedidosDelPago);

        // La línea de la tienda es de este pedido; la comisión (y la del taller, si es de una cotización) es del pago
        List<LineaReparto> reparto = p.getPagoId() == null ? List.of() : repartos.findByPagoId(p.getPagoId()).stream()
                .filter(r -> id.equals(r.getPedidoTiendaId()) || r.getPedidoTiendaId() == null)
                .sorted(Comparator.comparing(RepartoPago::getBeneficiarioTipo, Comparator.nullsLast(String::compareTo)))
                .map(r -> new LineaReparto(r.getBeneficiarioTipo(), r.getBeneficiarioNombre(), n(r.getMonto()), r.getEstado(),
                        r.getModoLiquidacion(), r.getFechaLiquidacion(), r.getNota(), r.getPedidoTiendaId() == null))
                .toList();
        var devoluciones = p.getPagoId() == null ? List.<com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Reembolso>of()
                : reembolsos.findByPagoId(p.getPagoId()).stream()
                .filter(b -> b.getPedidoTiendaId() == null || id.equals(b.getPedidoTiendaId()))
                .map(b -> new com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Reembolso(
                        n(b.getMonto()), b.getEstado(), b.getMotivo(), b.getFechaCreacion(), b.getFechaRealizado()))
                .toList();

        Entrega entrega = entrega(p, ahora);
        Problema problema = Boolean.TRUE.equals(p.getProblemaReportado())
                ? new Problema(p.getProblemaDetalle(), p.getFechaProblema(), p.getResolucionProblema(), p.getMotivoResolucion(),
                        p.getFechaResolucionProblema())
                : null;

        return new Pedido(p.getId(), p.esCatalogo() ? PedidoTienda.ORIGEN_CATALOGO : PedidoTienda.ORIGEN_TALLER, p.getEstado(),
                p.getFechaCreacion(), p.getPlazoDespachoHasta(), SemaforoPedido.despacho(p, ahora), SemaforoPedido.retiro(p, ahora),
                parteTienda, parteCliente, parteTaller, p.getVehiculoDescripcion(), lineas, n(p.getSubtotal()), n(p.getIva()),
                n(p.getTotal()), lineaDeTiempo(p), datosPago, reparto, devoluciones, entrega, problema);
    }

    private static Entrega entrega(PedidoTienda p, LocalDateTime ahora) {
        if (!p.esCatalogo()) {
            return new Entrega("TALLER", p.getTallerNombre(), p.getTallerTelefono(), p.getTallerDireccion(), p.getTallerCiudad(),
                    null, 0, null, null, null, null, null);
        }
        if (PedidoTienda.RETIRO.equals(p.getEntrega())) {
            Long dias = SemaforoPedido.retiro(p, ahora) == null ? null : RetiroPendienteService.diasSinRetirar(p, ahora);
            return new Entrega(PedidoTienda.RETIRO, null, null, p.getTiendaDireccion(), p.getTiendaCiudad(), null, 0,
                    p.getTiendaHorario(), p.getFechaListoRetiro(), p.getFechaRetirado(), dias, p.getRecordatoriosRetiro());
        }
        return new Entrega(PedidoTienda.ENVIO, p.getEnvioReceptor(), p.getEnvioTelefono(), p.getEnvioDireccion(),
                p.getEnvioCiudad(), p.getEnvioIndicaciones(), n(p.getCostoEnvio()), null, null, null, null, null);
    }

    /** Los estados por los que pasó el pedido, en orden, a partir de las fechas que guarda cada paso. */
    static List<Evento> lineaDeTiempo(PedidoTienda p) {
        boolean retiro = PedidoTienda.RETIRO.equals(p.getEntrega());
        List<Evento> e = new ArrayList<>();
        agregar(e, "Pagado", p.getFechaCreacion(), null);
        agregar(e, "En preparación", p.getFechaPreparacion(), null);
        agregar(e, "No despachado", p.getFechaNoDespacho(), p.getMotivoNoDespacho());
        agregar(e, "Despachado", p.getFechaDespacho(), p.getNotaDespacho());
        agregar(e, p.esCatalogo() ? "Recibido" : "Recibido por el taller", p.getFechaRecibido(), null);
        agregar(e, "Listo para retirar", p.getFechaListoRetiro(), null);
        agregar(e, "Retiro en revisión de DriveMaster", p.getRevisionRetiroDesde(), null);
        agregar(e, "Retiro resuelto", p.getFechaResolucionRetiro(), p.getMotivoResolucionRetiro());
        agregar(e, "Retirado en la tienda", p.getFechaRetirado(), null);
        agregar(e, Boolean.TRUE.equals(p.getRecepcionAutomatica()) ? "Recibido (confirmado automáticamente)"
                : "Recibido por el cliente", retiro ? null : p.getFechaRecibidoCliente(), null);
        agregar(e, "Problema reportado", p.getFechaProblema(), p.getProblemaDetalle());
        agregar(e, "Problema resuelto", p.getFechaResolucionProblema(), p.getMotivoResolucion());
        e.sort(Comparator.comparing(Evento::fecha));
        return e;
    }

    private static void agregar(List<Evento> e, String etiqueta, LocalDateTime fecha, String detalle) {
        if (fecha != null) e.add(new Evento(etiqueta, fecha, detalle == null || detalle.isBlank() ? null : detalle));
    }

    // ─── Cliente ────────────────────────────────────────────────────────────

    public Cliente cliente(String id) {
        Usuario u = existe(mongo.findById(id, Usuario.class), "Cliente no encontrado");
        if (!"CLIENTE".equals(u.getRol())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");

        List<Vehiculo> vehiculos = mongo.find(Query.query(Criteria.where("usuarioId").is(id)),
                        com.drivemaster.drivemaster.model.Vehiculo.class).stream()
                .map(v -> new Vehiculo(v.getId(), descripcion(v.getMarca(), v.getModelo(), v.getAnio()), v.getPlaca(), v.getKilometraje()))
                .toList();

        Criteria suyos = Criteria.where("clienteId").is(id);
        List<PedidoTienda> pedidos = mongo.find(Query.query(suyos).with(Sort.by(Sort.Direction.DESC, "fechaCreacion")),
                PedidoTienda.class);
        double comprado = pedidos.stream().filter(p -> !SIN_VENTA.contains(p.getEstado())).mapToDouble(p -> n(p.getTotal())).sum();

        List<OrdenServicio> ordenes = mongo.find(Query.query(Criteria.where("usuarioId").is(id))
                .with(Sort.by(Sort.Direction.DESC, "fechaCreacion")), OrdenServicio.class);

        return new Cliente(u.getId(), u.getNombre(), PlataformaConsultasService.enmascarar(u.getCorreo()), u.getTelefono(),
                u.getIdentificacion(), u.getCiudad(), u.getDireccion(), u.getProveedor(), !Boolean.FALSE.equals(u.getActivo()),
                local(u.getFechaCreacion()), local(u.getUltimoLogin()), vehiculos,
                pedidos.stream().limit(RECIENTES).map(PlataformaDetalleService::resumen).toList(), pedidos.size(), comprado,
                ordenes.stream().limit(RECIENTES).map(PlataformaDetalleService::resumen).toList(), ordenes.size());
    }

    // ─── Negocio ────────────────────────────────────────────────────────────

    public Negocio negocio(String tipo, String id) {
        if ("TIENDA".equalsIgnoreCase(tipo)) return tienda(id);
        if ("TALLER".equalsIgnoreCase(tipo)) return taller(id);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de negocio es TIENDA o TALLER");
    }

    private Negocio tienda(String id) {
        Tienda t = existe(mongo.findById(id, Tienda.class), "Tienda no encontrada");
        Criteria deLaTienda = Criteria.where("tiendaId").is(id);
        long totalProductos = mongo.count(Query.query(deLaTienda), Producto.class);
        long activos = mongo.count(Query.query(Criteria.where("tiendaId").is(id).and("activo").ne(false)), Producto.class);
        List<ProductoResumen> productos = mongo.find(Query.query(Criteria.where("tiendaId").is(id))
                        .with(Sort.by(Sort.Direction.DESC, "_id")).limit(RECIENTES), Producto.class).stream()
                .map(p -> new ProductoResumen(p.getId(), p.getNombre(), p.getMarca(), n(p.getPrecioVenta()),
                        "ENCARGO".equals(p.getTipo()) ? null : p.getStockActual(), p.getTipo(), !Boolean.FALSE.equals(p.getActivo())))
                .toList();

        List<PedidoTienda> pedidos = mongo.find(Query.query(Criteria.where("tiendaId").is(id))
                .with(Sort.by(Sort.Direction.DESC, "fechaCreacion")), PedidoTienda.class);
        double vendido = pedidos.stream().filter(p -> !SIN_VENTA.contains(p.getEstado())).mapToDouble(p -> n(p.getTotal())).sum();

        return new Negocio("TIENDA", t.getId(), t.getNombre(), t.getNit(), t.getEstadoAprobacion(), Boolean.TRUE.equals(t.getActivo()),
                t.getMotivoRechazo(), t.getFechaRegistro(), t.getFechaAprobacion(), cuenta(t.getUsuarioId()), t.getTelefono(),
                t.getCiudad(), t.getBarrio(), t.getDireccion(), t.getLatitud(), t.getLongitud(), t.getHorario(), t.getLogoUrl(),
                null, null, t.getCostoEnvio(), null, null, null, null, totalProductos, activos, productos,
                pedidos.stream().limit(RECIENTES).map(PlataformaDetalleService::resumen).toList(), null,
                totales("TIENDA", id, pedidos.size(), vendido));
    }

    private Negocio taller(String id) {
        Taller t = existe(mongo.findById(id, Taller.class), "Taller no encontrado");
        List<OrdenServicio> ordenes = mongo.find(Query.query(Criteria.where("tallerId").is(id))
                .with(Sort.by(Sort.Direction.DESC, "fechaCreacion")), OrdenServicio.class);
        double vendido = ordenes.stream().filter(o -> o.getFechaPago() != null).mapToDouble(o -> n(o.getTotal())).sum();
        List<Mecanico> mecanicos = t.getMecanicos() == null ? List.of() : t.getMecanicos().stream()
                .map(m -> new Mecanico(m.getNombre(), m.getEspecialidad(), !Boolean.FALSE.equals(m.getActivo()))).toList();

        return new Negocio("TALLER", t.getId(), t.getNombre(), t.getNit(), t.getEstadoAprobacion(), Boolean.TRUE.equals(t.getActivo()),
                t.getMotivoRechazo(), t.getFechaRegistro(), t.getFechaAprobacion(), cuenta(t.getUsuarioId()), t.getTelefono(),
                t.getCiudad(), t.getBarrio(), t.getDireccion(), t.getLatitud(), t.getLongitud(), t.getHorario(), t.getLogoUrl(),
                t.getRating(), t.getCalificaciones(), null, t.getEspecialidades(), t.getTarifaHora(), t.getPrecioManoObra(),
                mecanicos, 0, 0, null, null, ordenes.stream().limit(RECIENTES).map(PlataformaDetalleService::resumen).toList(),
                totales("TALLER", id, ordenes.size(), vendido));
    }

    /** Su parte del reparto por estado (sin la comisión, que es de la plataforma). */
    private Totales totales(String tipo, String id, long pedidos, double vendido) {
        double liquidado = 0, porLiquidar = 0, enEspera = 0;
        for (Object[] fila : repartos.resumenPorEstado(tipo, id)) {
            String estado = String.valueOf(fila[0]);
            double suma = fila[2] instanceof Number num ? num.doubleValue() : 0;
            switch (estado) {
                case RepartoPago.LIQUIDADO -> liquidado += suma;
                case RepartoPago.POR_LIQUIDAR, RepartoPago.FALLIDO, RepartoPago.PROCESANDO -> porLiquidar += suma;
                case RepartoPago.PENDIENTE -> enEspera += suma;
                default -> { }
            }
        }
        return new Totales(pedidos, vendido, liquidado, porLiquidar, enEspera);
    }

    private Parte cuenta(String usuarioId) {
        if (usuarioId == null) return null;
        Usuario u = mongo.findById(usuarioId, Usuario.class);
        return u == null ? null : new Parte(u.getId(), u.getNombre(), PlataformaConsultasService.enmascarar(u.getCorreo()),
                u.getTelefono(), u.getCiudad());
    }

    // ─── Piezas ─────────────────────────────────────────────────────────────

    private static PedidoResumen resumen(PedidoTienda p) {
        return new PedidoResumen(p.getId(), p.getTiendaNombre(), p.getEstado(), n(p.getTotal()), p.getFechaCreacion());
    }

    private static SolicitudResumen resumen(OrdenServicio o) {
        String taller = o.getTallerNombre() != null ? o.getTallerNombre() : o.getTallerElegidoNombre();
        return new SolicitudResumen(o.getId(), taller, o.getVehiculoDescripcion(), o.getEstado(), n(o.getTotal()), o.getFechaCreacion());
    }

    static String descripcion(String marca, String modelo, Integer anio) {
        String d = Stream.of(marca, modelo, anio == null ? null : String.valueOf(anio))
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(" "));
        return d.isEmpty() ? null : d;
    }

    private static <T> T existe(T valor, String mensaje) {
        if (valor == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, mensaje);
        return valor;
    }

    private static double n(Double d) {
        return d == null ? 0 : d;
    }

    private static LocalDateTime local(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneId.systemDefault());
    }
}
