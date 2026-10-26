package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CrearOrdenServicioRequest;
import com.drivemaster.drivemaster.dto.OrdenServicioDTO;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Pago;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrdenServicioServiceImpl implements OrdenServicioService {

    private static final Logger log = LoggerFactory.getLogger(OrdenServicioServiceImpl.class);

    private static final Set<String> ESTADOS_CANCELABLES = Set.of("SOLICITADA", "COTIZADA");

    // Mismos métodos que acepta VentaServiceImpl.registrarVentaTaller.
    private static final Set<String> METODOS_PAGO_TALLER = Set.of("EFECTIVO", "TRANSFERENCIA");

    private final OrdenServicioRepository ordenServicioRepository;
    private final VehiculoService vehiculoService;
    private final TallerService tallerService;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final VentaService ventaService;
    private final MantenimientoService mantenimientoService;
    private final CotizacionRepository cotizacionRepository;

    public OrdenServicioServiceImpl(OrdenServicioRepository ordenServicioRepository,
                                     VehiculoService vehiculoService,
                                     TallerService tallerService,
                                     ProductoService productoService,
                                     UsuarioService usuarioService,
                                     VentaService ventaService,
                                     MantenimientoService mantenimientoService,
                                     CotizacionRepository cotizacionRepository) {
        this.cotizacionRepository = cotizacionRepository;
        this.ordenServicioRepository = ordenServicioRepository;
        this.vehiculoService = vehiculoService;
        this.tallerService = tallerService;
        this.productoService = productoService;
        this.usuarioService = usuarioService;
        this.ventaService = ventaService;
        this.mantenimientoService = mantenimientoService;
    }

    @Override
    public OrdenServicioDTO cotizar(String correoUsuario, CrearOrdenServicioRequest request) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoUsuario);

        // Valida que el vehículo pertenezca al usuario
        Vehiculo vehiculo = vehiculoService.obtenerEntidad(request.getVehiculoId());
        if (!vehiculo.getUsuarioId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este vehículo no le pertenece");
        }

        Producto producto = productoService.obtenerPorId(request.getProductoId());
        if ("ENCARGO".equals(producto.getTipo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El repuesto seleccionado es por encargo y no está disponible para taller");
        }
        if (producto.getStockActual() == null || producto.getStockActual() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El repuesto seleccionado no tiene stock disponible");
        }

        Taller taller = tallerService.obtenerEntidad(request.getTallerId());
        if (taller.getActivo() == null || !taller.getActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El taller seleccionado no está disponible");
        }

        double precioRepuesto = producto.getPrecioVenta() != null ? producto.getPrecioVenta() : 0.0;
        double precioManoObra = taller.getPrecioManoObra() != null ? taller.getPrecioManoObra() : 0.0;

        OrdenServicio orden = OrdenServicio.builder()
                .usuarioId(usuario.getId())
                .vehiculoId(vehiculo.getId())
                .tallerId(taller.getId())
                .productoId(producto.getId())
                .productoNombre(producto.getNombre())
                .productoMarca(producto.getMarca())
                .tallerNombre(taller.getNombre())
                .vehiculoDescripcion(vehiculo.getMarca() + " " + vehiculo.getModelo() + " " + vehiculo.getAnio())
                .vehiculoImagenUrl(vehiculo.getImagenUrl())
                .precioRepuesto(precioRepuesto)
                .precioManoObra(precioManoObra)
                .total(precioRepuesto + precioManoObra)
                .estado("COTIZADA")
                .fechaCreacion(LocalDateTime.now())
                .build();

        orden = ordenServicioRepository.save(orden);
        return toDTO(orden);
    }

    /**
     * El cliente informa cómo va a pagar (o que ya transfirió). No mueve la orden
     * ni genera venta: antes el cliente podía marcar su propia orden como PAGADA
     * sin haber pagado. Ahora el pago lo confirma el taller.
     */
    @Override
    public OrdenServicioDTO reportarPago(String correoUsuario, String ordenId, String metodoPago, String referencia) {
        OrdenServicio orden = obtenerYValidarPropietario(correoUsuario, ordenId);
        validarCotizadaConStock(orden);

        String metodo = (metodoPago == null || metodoPago.isBlank()) ? "EFECTIVO" : metodoPago.trim().toUpperCase();
        if (!METODOS_PAGO_TALLER.contains(metodo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los servicios de taller solo se pagan con efectivo o transferencia");
        }

        orden.setMetodoPago(metodo);
        orden.setReferenciaPago(referencia != null && !referencia.isBlank() ? referencia.trim() : null);
        orden.setFechaPagoReportado(LocalDateTime.now());
        orden = ordenServicioRepository.save(orden);
        return toDTO(orden);
    }

    /** El taller confirma que recibió el pago: se registra la venta y la orden pasa a PAGADA. */
    @Override
    public OrdenServicioDTO confirmarPago(String correoTaller, String ordenId) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = obtenerOrden(ordenId);
        validarPropiedadTaller(taller, orden);
        Producto producto = validarCotizadaConStock(orden);

        Usuario cliente = usuarioService.obtenerPorId(orden.getUsuarioId());
        // Si el cliente no lo reportó en la app, se asume pago en efectivo en el taller.
        String metodo = orden.getMetodoPago() != null ? orden.getMetodoPago() : "EFECTIVO";
        String referencia = orden.getReferenciaPago();
        double total = orden.getTotal() != null ? orden.getTotal() : 0.0;

        // El repuesto se factura como STOCK (genera salida de inventario) y la mano de obra
        // como MANO_OBRA, para que el subtotal de la factura sume el total pactado en la orden.
        List<DetalleVenta> detalles = new ArrayList<>();

        DetalleVenta detalleRepuesto = new DetalleVenta();
        detalleRepuesto.setProductoId(producto.getId());
        detalleRepuesto.setNombre(producto.getNombre());
        detalleRepuesto.setImagenUrl(producto.getImagenUrl());
        detalleRepuesto.setPrecioUnitario(orden.getPrecioRepuesto());
        detalleRepuesto.setCantidad(1);
        detalleRepuesto.setSubtotal(orden.getPrecioRepuesto());
        detalleRepuesto.setTipo("STOCK");
        detalleRepuesto.setStockActual(producto.getStockActual());
        detalles.add(detalleRepuesto);

        double manoObra = orden.getPrecioManoObra() != null ? orden.getPrecioManoObra() : 0.0;
        if (manoObra > 0) {
            DetalleVenta detalleManoObra = new DetalleVenta();
            detalleManoObra.setProductoId(orden.getTallerId());
            detalleManoObra.setNombre("Mano de obra - " + orden.getTallerNombre());
            detalleManoObra.setPrecioUnitario(manoObra);
            detalleManoObra.setCantidad(1);
            detalleManoObra.setSubtotal(manoObra);
            detalleManoObra.setTipo("MANO_OBRA");
            detalles.add(detalleManoObra);
        }

        Pago pago = new Pago(metodo, total, LocalDateTime.now(), referencia);

        Venta venta = new Venta();
        venta.setClienteId(cliente.getId());
        venta.setUsuarioId(cliente.getId());
        venta.setTipoVenta("TALLER");
        venta.setOrdenServicioId(orden.getId());
        venta.setTallerId(orden.getTallerId());
        venta.setProductos(detalles);
        venta.setPagos(List.of(pago));
        venta.setTotal(total);
        venta.setEstado("PAGADA");

        Venta ventaGuardada = ventaService.registrarVentaTaller(venta);

        orden.setEstado("PAGADA");
        orden.setFechaPago(LocalDateTime.now());
        orden.setVentaId(ventaGuardada.getId());
        orden = ordenServicioRepository.save(orden);
        return toDTO(orden);
    }

    @Override
    public OrdenServicioDTO cancelar(String correoUsuario, String ordenId) {
        OrdenServicio orden = obtenerYValidarPropietario(correoUsuario, ordenId);
        if (!ESTADOS_CANCELABLES.contains(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede cancelar una orden en estado " + orden.getEstado());
        }
        orden.setEstado("CANCELADA");
        orden = ordenServicioRepository.save(orden);
        return toDTO(orden);
    }

    @Override
    public OrdenServicioDTO iniciarServicio(String correoTaller, String ordenId) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = obtenerOrden(ordenId);
        validarPropiedadTaller(taller, orden);

        if (!"PAGADA".equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La orden no está en estado PAGADA (estado actual: " + orden.getEstado() + ")");
        }

        orden.setEstado("EN_PROCESO");
        orden.setFechaInicio(LocalDateTime.now());
        orden = ordenServicioRepository.save(orden);

        // Se le avisa al cliente que su vehículo ya entró al taller. Si el correo
        // falla, la orden ya quedó guardada como EN_PROCESO.
        try {
            notificarCliente(orden, false);
        } catch (Exception e) {
            log.warn("No se pudo notificar el inicio de la orden {}: {}", orden.getId(), e.getMessage());
        }

        return toDTO(orden);
    }

    @Override
    public OrdenServicioDTO finalizarServicio(String correoTaller, String ordenId, String observaciones) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = obtenerOrden(ordenId);
        validarPropiedadTaller(taller, orden);

        if (!"EN_PROCESO".equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La orden no está en estado EN_PROCESO (estado actual: " + orden.getEstado() + ")");
        }

        orden.setEstado("FINALIZADA");
        orden.setFechaFinalizacion(LocalDateTime.now());
        if (observaciones != null && !observaciones.isBlank()) {
            orden.setObservaciones(observaciones);
        }
        orden = ordenServicioRepository.save(orden);

        // El servicio en taller cuenta como mantenimiento: reinicia el conteo de
        // kilómetros del vehículo y le avisa al cliente. Si el correo falla, la orden
        // ya quedó guardada como finalizada.
        try {
            Vehiculo vehiculo = vehiculoService.obtenerEntidad(orden.getVehiculoId());
            mantenimientoService.registrarMantenimientoRealizado(vehiculo);
            mantenimientoService.notificarMantenimientoRealizado(vehiculo);
            notificarCliente(orden, true);
        } catch (Exception e) {
            log.warn("No se pudo actualizar el mantenimiento del vehiculo de la orden {}: {}",
                    orden.getId(), e.getMessage());
        }

        return toDTO(orden);
    }

    /** Notifica al cliente cuando su vehículo entra o sale del taller. */
    private void notificarCliente(OrdenServicio orden, boolean finalizado) {
        Usuario cliente = usuarioService.obtenerPorId(orden.getUsuarioId());
        Vehiculo vehiculo = vehiculoService.obtenerEntidad(orden.getVehiculoId());
        if (finalizado) {
            mantenimientoService.notificarServicioFinalizado(cliente, vehiculo, orden);
        } else {
            mantenimientoService.notificarServicioIniciado(cliente, vehiculo, orden);
        }
    }

    @Override
    public OrdenServicioDTO programarCita(String correoTaller, String ordenId, LocalDateTime fechaProgramada) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = obtenerOrden(ordenId);
        validarPropiedadTaller(taller, orden);

        if (fechaProgramada == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha programada es obligatoria");
        }
        if (fechaProgramada.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha programada no puede estar en el pasado");
        }

        orden.setFechaProgramada(fechaProgramada);
        orden.setCitaNotificada(false);
        orden = ordenServicioRepository.save(orden);

        // Se avisa al cliente por correo con la fecha y hora de ingreso.
        try {
            Usuario cliente = usuarioService.obtenerPorId(orden.getUsuarioId());
            Vehiculo vehiculo = vehiculoService.obtenerEntidad(orden.getVehiculoId());
            mantenimientoService.notificarCitaProgramada(cliente, vehiculo, fechaProgramada);
            orden.setCitaNotificada(true);
            orden = ordenServicioRepository.save(orden);
        } catch (Exception e) {
            log.warn("No se pudo notificar la cita programada de la orden {}: {}",
                    orden.getId(), e.getMessage());
        }

        return toDTO(orden);
    }

    @Override
    public List<OrdenServicioDTO> listarPorUsuario(String correoUsuario) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoUsuario);
        return ordenServicioRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuario.getId())
                .stream().filter(this::esFlujoV1).map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<OrdenServicioDTO> listarPorTaller(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        return ordenServicioRepository.findByTallerIdOrderByFechaCreacionDesc(taller.getId())
                .stream().filter(this::esFlujoV1).map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<OrdenServicioDTO> historialPorVehiculo(String correoUsuario, String vehiculoId) {
        // Valida propiedad del vehículo (lanza 403/404 si no corresponde)
        vehiculoService.obtenerDeUsuario(correoUsuario, vehiculoId);
        List<OrdenServicio> ordenes = ordenServicioRepository
                .findByVehiculoIdAndEstadoIn(vehiculoId, List.of("FINALIZADA", EstadoOrden.ENTREGADA)).stream()
                .filter(o -> esFlujoV1(o) ? "FINALIZADA".equals(o.getEstado()) : EstadoOrden.ENTREGADA.equals(o.getEstado()))
                .toList();
        // Las cotizaciones de las órdenes del marketplace (total y repuestos), en una sola consulta
        Map<String, Cotizacion> cotizaciones = cotizacionRepository.findAllById(ordenes.stream()
                        .map(OrdenServicio::getCotizacionId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Cotizacion::getId, Function.identity()));
        return ordenes.stream()
                .map(o -> esFlujoV1(o) ? toDTO(o) : historialMarketplace(o, cotizaciones.get(o.getCotizacionId())))
                .sorted(Comparator.comparing(OrdenServicioDTO::getFechaFinalizacion,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Un servicio del marketplace con la forma del historial: el trabajo (las fallas del diagnóstico), el taller, la
     * fecha de entrega, los repuestos y el total de la cotización aprobada.
     */
    static OrdenServicioDTO historialMarketplace(OrdenServicio o, Cotizacion cotizacion) {
        OrdenServicioDTO dto = new OrdenServicioDTO();
        dto.setId(o.getId());
        dto.setFlujo(EstadoOrden.FLUJO_MARKETPLACE);
        dto.setEstado(o.getEstado());
        dto.setVehiculoId(o.getVehiculoId());
        dto.setVehiculoDescripcion(o.getVehiculoDescripcion());
        dto.setTallerId(o.getTallerId());
        dto.setTallerNombre(o.getTallerNombre());
        dto.setFechaCreacion(o.getFechaCreacion());
        dto.setFechaProgramada(o.getFechaProgramada());
        dto.setFechaFinalizacion(o.getFechaEntrega());
        List<String> fallas = o.getDiagnostico() != null && o.getDiagnostico().getFallas() != null
                ? o.getDiagnostico().getFallas() : List.of();
        dto.setProductoNombre(fallas.isEmpty() ? "Diagnóstico y reparación" : String.join(", ", fallas));
        if (o.getComentarioCalificacion() != null && !o.getComentarioCalificacion().isBlank()) {
            dto.setObservaciones(o.getComentarioCalificacion());
        }
        if (cotizacion != null) {
            dto.setTotal(cotizacion.getTotal());
            dto.setPrecioRepuesto(cotizacion.getSubtotalRepuestos());
            dto.setPrecioManoObra(cotizacion.getSubtotalManoObra());
            dto.setRepuestos(cotizacion.getItems() == null ? List.of() : cotizacion.getItems().stream()
                    .filter(i -> CotizacionItem.REPUESTO.equals(i.getTipo()))
                    .map(i -> (i.getCantidad() == null ? 1 : i.getCantidad()) + " × "
                            + (i.getProductoNombre() != null ? i.getProductoNombre() : i.getDescripcion()))
                    .toList());
        } else {
            // Sin la cotización (borrada): al menos los repuestos que eligió el taller
            dto.setRepuestos(o.getRepuestos() == null ? List.of() : o.getRepuestos().stream()
                    .map(r -> (r.getCantidad() == null ? 1 : r.getCantidad()) + " × "
                            + (r.getProductoNombre() != null ? r.getProductoNombre() : r.getDescripcion()))
                    .toList());
        }
        return dto;
    }

    // ─── Helpers ─────────────────────────────────────────────

    private OrdenServicio obtenerOrden(String ordenId) {
        // Las órdenes del marketplace tienen su propio flujo (SolicitudServicioService):
        // los endpoints V1 no las ven ni las operan.
        return ordenServicioRepository.findById(ordenId)
                .filter(this::esFlujoV1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de servicio no encontrada"));
    }

    private boolean esFlujoV1(OrdenServicio orden) {
        return !EstadoOrden.FLUJO_MARKETPLACE.equals(orden.getFlujo());
    }

    private Producto validarCotizadaConStock(OrdenServicio orden) {
        if (!"COTIZADA".equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La orden no está en estado COTIZADA (estado actual: " + orden.getEstado() + ")");
        }

        Producto producto = productoService.obtenerPorId(orden.getProductoId());
        if ("ENCARGO".equals(producto.getTipo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El repuesto es por encargo y no se puede cobrar como venta de taller");
        }
        if (producto.getStockActual() == null || producto.getStockActual() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El repuesto ya no tiene stock disponible");
        }
        return producto;
    }

    private OrdenServicio obtenerYValidarPropietario(String correoUsuario, String ordenId) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoUsuario);
        OrdenServicio orden = obtenerOrden(ordenId);
        if (!orden.getUsuarioId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta orden no le pertenece");
        }
        return orden;
    }

    private void validarPropiedadTaller(Taller taller, OrdenServicio orden) {
        if (!orden.getTallerId().equals(taller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta orden no pertenece a su taller");
        }
    }

    private OrdenServicioDTO toDTO(OrdenServicio o) {
        return new OrdenServicioDTO(o.getId(), o.getVehiculoId(), o.getVehiculoDescripcion(),
                o.getVehiculoImagenUrl(),
                o.getProductoId(), o.getProductoNombre(), o.getProductoMarca(),
                o.getTallerId(), o.getTallerNombre(),
                o.getPrecioRepuesto(), o.getPrecioManoObra(), o.getTotal(),
                o.getEstado(), o.getFechaCreacion(), o.getFechaPago(), o.getFechaInicio(),
                o.getFechaFinalizacion(), o.getObservaciones(),
                o.getFechaProgramada(), o.getCitaNotificada(),
                OrdenServicioDTO.estaRetrasada(
                        o.getEstado(), o.getFechaCreacion(), o.getFechaPago(), o.getFechaInicio()),
                o.getMetodoPago(), o.getReferenciaPago(), o.getFechaPagoReportado(),
                o.getFlujo(), null);
    }

    @Override
public List<OrdenServicioDTO> listarPorTallerAdmin(String tallerId) {
    tallerService.obtenerEntidad(tallerId); // valida que el taller exista (404 si no)
    return ordenServicioRepository.findByTallerIdOrderByFechaCreacionDesc(tallerId)
            .stream().filter(this::esFlujoV1).map(this::toDTO).collect(Collectors.toList());
}
}