package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AceptarSolicitudRequest;
import com.drivemaster.drivemaster.dto.CrearSolicitudServicioRequest;
import com.drivemaster.drivemaster.dto.DiagnosticoRequest;
import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.SeleccionRepuestosRequest;
import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import com.drivemaster.drivemaster.model.Diagnostico;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import com.drivemaster.drivemaster.util.Coordenadas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

@Service
public class SolicitudServicioServiceImpl implements SolicitudServicioService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudServicioServiceImpl.class);

    private static final int MAX_FOTOS = 5;
    private static final int MAX_SINTOMAS = 1500;
    private static final int MAX_TEXTO_DIAGNOSTICO = 3000;
    private static final int MAX_FALLAS = 20;
    private static final int MAX_FOTOS_DIAGNOSTICO = 8;
    private static final int MAX_REPUESTOS = 30;
    private static final int MAX_CANTIDAD = 99;
    private static final Set<String> CANCELABLES_POR_CLIENTE =
            Set.of(EstadoOrden.ENVIADA, EstadoOrden.ASIGNADA_A_TALLER, EstadoOrden.REASIGNADA);

    private final OrdenServicioRepository ordenRepository;
    private final TallerRepository tallerRepository;
    private final VehiculoService vehiculoService;
    private final UsuarioService usuarioService;
    private final TallerService tallerService;
    private final CloudinaryService cloudinaryService;
    private final TransicionOrdenService transiciones;
    private final AsignacionTallerService asignacion;
    private final ParametrosMarketplace parametros;
    private final AvisosSolicitudServicio avisos;
    private final ProductoService productoService;
    private final BuscadorRepuestosService buscador;
    private final LiquidacionService liquidacion;
    private final CalificacionTallerService calificaciones;
    private final HorarioTallerService horarios;

    public SolicitudServicioServiceImpl(OrdenServicioRepository ordenRepository,
                                        TallerRepository tallerRepository,
                                        VehiculoService vehiculoService,
                                        UsuarioService usuarioService,
                                        TallerService tallerService,
                                        CloudinaryService cloudinaryService,
                                        TransicionOrdenService transiciones,
                                        AsignacionTallerService asignacion,
                                        ParametrosMarketplace parametros,
                                        AvisosSolicitudServicio avisos,
                                        ProductoService productoService,
                                        BuscadorRepuestosService buscador,
                                        LiquidacionService liquidacion,
                                        CalificacionTallerService calificaciones,
                                        HorarioTallerService horarios) {
        this.horarios = horarios;
        this.calificaciones = calificaciones;
        this.liquidacion = liquidacion;
        this.productoService = productoService;
        this.buscador = buscador;
        this.ordenRepository = ordenRepository;
        this.tallerRepository = tallerRepository;
        this.vehiculoService = vehiculoService;
        this.usuarioService = usuarioService;
        this.tallerService = tallerService;
        this.cloudinaryService = cloudinaryService;
        this.transiciones = transiciones;
        this.asignacion = asignacion;
        this.parametros = parametros;
        this.avisos = avisos;
    }

    // ─── Cliente ─────────────────────────────────────────────────────────────

    @Override
    public SolicitudServicioDTO crear(String correoCliente, CrearSolicitudServicioRequest r) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);

        if (r.getVehiculoId() == null || r.getVehiculoId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona el vehículo");
        }
        Vehiculo vehiculo = vehiculoService.obtenerEntidad(r.getVehiculoId());
        if (!cliente.getId().equals(vehiculo.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este vehículo no le pertenece");
        }
        String sintomas = r.getSintomas() != null ? r.getSintomas().trim() : "";
        if (sintomas.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Describe el problema o los síntomas (mínimo 10 caracteres)");
        }
        if (sintomas.length() > MAX_SINTOMAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción es demasiado larga");
        }
        if (r.getCiudad() == null || r.getCiudad().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la ciudad");
        }
        List<String> fotos = r.getFotos() != null
                ? r.getFotos().stream().filter(f -> f != null && f.startsWith("http")).toList()
                : List.of();
        if (fotos.size() > MAX_FOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_FOTOS + " fotos");
        }
        if (ordenRepository.existsByFlujoAndVehiculoIdAndEstadoIn(
                EstadoOrden.FLUJO_MARKETPLACE, vehiculo.getId(), EstadoOrden.ACTIVOS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este vehículo ya tiene una solicitud en curso. Revisa \"Mis servicios\".");
        }
        Coordenadas.validar(r.getLatitud(), r.getLongitud(), false);
        Taller elegido = tallerElegido(r.getTallerId());

        LocalDateTime ahora = LocalDateTime.now();
        OrdenServicio orden = OrdenServicio.builder()
                .flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .usuarioId(cliente.getId())
                .vehiculoId(vehiculo.getId())
                .vehiculoDescripcion(vehiculo.getMarca() + " " + vehiculo.getModelo() + " " + vehiculo.getAnio())
                .vehiculoImagenUrl(vehiculo.getImagenUrl())
                .sintomas(sintomas)
                .fotos(new ArrayList<>(fotos))
                .ciudad(r.getCiudad().trim())
                .barrio(r.getBarrio() != null ? r.getBarrio().trim() : null)
                .direccion(limpiarDireccion(r.getDireccion()))
                .latitud(r.getLatitud())
                .longitud(r.getLongitud())
                .preferenciaHorario(r.getPreferenciaHorario())
                .tallerElegidoId(elegido != null ? elegido.getId() : null)
                .tallerElegidoNombre(elegido != null ? elegido.getNombre() : null)
                .estado(EstadoOrden.ENVIADA)
                .fechaCreacion(ahora)
                .eventos(new ArrayList<>(List.of(EventoOrden.builder()
                        .estado(EstadoOrden.ENVIADA)
                        .fecha(ahora)
                        .actorRol(EstadoOrden.CLIENTE)
                        .actorId(cliente.getId())
                        .actorNombre(cliente.getNombre())
                        .nota("Solicitud de diagnóstico enviada")
                        .build())))
                .build();
        orden = ordenRepository.save(orden);

        // La solicitud ya quedó guardada: si la asignación falla, el job la retoma
        // (las ENVIADA se reintentan) y el cliente no recibe un error.
        try {
            orden = asignacion.asignarSiguiente(orden);
        } catch (Exception e) {
            log.warn("No se pudo asignar la solicitud {} al crearla; la retoma el job: {}", orden.getId(), e.getMessage());
        }
        return toDTO(orden, Vista.CLIENTE);
    }

    /** Taller elegido en el mapa: debe existir, estar aprobado y activo. Null si no se eligió. */
    private Taller tallerElegido(String tallerId) {
        if (tallerId == null || tallerId.isBlank()) return null;
        Taller taller = tallerRepository.findById(tallerId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El taller elegido no existe"));
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion()) || !Boolean.TRUE.equals(taller.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El taller elegido no está disponible. Elige otro o usa la asignación automática.");
        }
        return taller;
    }

    @Override
    public String subirFoto(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una imagen");
        }
        if (archivo.getContentType() == null || !archivo.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser una imagen");
        }
        try {
            return cloudinaryService.subirImagen(archivo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo subir la imagen");
        }
    }

    @Override
    public List<SolicitudServicioDTO> misSolicitudes(String correoCliente) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        return toDTOs(ordenRepository.findByFlujoAndUsuarioIdOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, cliente.getId()),
                Vista.CLIENTE);
    }

    @Override
    public SolicitudServicioDTO cancelarPorCliente(String correoCliente, String id, String motivo) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        OrdenServicio orden = obtenerMarketplace(id);
        if (!cliente.getId().equals(orden.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no le pertenece");
        }
        if (!CANCELABLES_POR_CLIENTE.contains(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La solicitud ya no se puede cancelar desde la app (estado " + orden.getEstado() + ")");
        }
        String texto = motivo != null && !motivo.isBlank() ? motivo.trim() : "Cancelada por el cliente";
        String tallerAnterior = orden.getTallerId();
        OrdenServicio cancelada = transiciones.transicionar(orden, EstadoOrden.CANCELADA,
                new Actor(EstadoOrden.CLIENTE, cliente.getId(), cliente.getNombre()), texto, null,
                new Update().set("motivoCierre", texto));
        avisos.solicitudRetirada(tallerAnterior, cancelada, "El cliente canceló la solicitud");
        return toDTO(cancelada, Vista.CLIENTE);
    }

    @Override
    public SolicitudServicioDTO obtener(String correo, String id) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);
        OrdenServicio orden = obtenerMarketplace(id);
        String actor = EstadoOrden.actorDeRol(usuario.getRol());

        if (EstadoOrden.ADMIN.equals(actor)) return toDTO(orden, Vista.ADMIN);
        if (EstadoOrden.CLIENTE.equals(actor) && usuario.getId().equals(orden.getUsuarioId())) {
            return toDTO(orden, Vista.CLIENTE);
        }
        if (EstadoOrden.TALLER.equals(actor)) {
            Taller taller = tallerService.obtenerPorUsuario(correo);
            if (taller.getId().equals(orden.getTallerId())) return toDTO(orden, Vista.TALLER);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a esta solicitud");
    }

    // ─── Taller ──────────────────────────────────────────────────────────────

    @Override
    public List<SolicitudServicioDTO> bandejaTaller(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        return toDTOs(ordenRepository.findByFlujoAndTallerIdOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, taller.getId()),
                Vista.TALLER);
    }

    @Override
    public List<SolicitudServicioDTO> citasTaller(String tallerId, LocalDate desde, LocalDate hasta) {
        return toDTOs(ordenRepository.citasDelTaller(EstadoOrden.FLUJO_MARKETPLACE, tallerId, desde.atStartOfDay(),
                hasta.plusDays(1).atStartOfDay(),
                List.of(EstadoOrden.CANCELADA, EstadoOrden.RECHAZADA, EstadoOrden.REASIGNADA)), Vista.TALLER);
    }

    /**
     * El taller acepta y propone la cita de revisión. Con PAGO_DIAGNOSTICO_REQUERIDO
     * la solicitud sigue ASIGNADA_A_TALLER hasta que el cliente paga (Fase 3); sin él
     * pasa directo a EN_REVISION.
     */
    @Override
    public SolicitudServicioDTO aceptar(String correoTaller, String id, AceptarSolicitudRequest r) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        if (!EstadoOrden.ASIGNADA_A_TALLER.equals(orden.getEstado()) || Boolean.TRUE.equals(orden.getAceptadaPorTaller())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud no está esperando respuesta");
        }
        if (r == null || r.getFechaRevision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la fecha y hora de la revisión");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (!r.getFechaRevision().isAfter(ahora)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de revisión debe ser futura");
        }
        // Solo en un día y una hora en que el taller atiende (si ya configuró su horario por días)
        horarios.validarCita(taller.getId(), r.getFechaRevision());
        Mecanico mecanico = mecanicoActivo(taller, r.getMecanicoId());

        boolean pagoRequerido = parametros.pagoDiagnosticoRequerido();
        Update cambios = new Update()
                .set("aceptadaPorTaller", true)
                .set("fechaAceptacion", ahora)
                .set("fechaProgramada", r.getFechaRevision())
                .set("tarifaDiagnostico", parametros.tarifaDiagnostico())
                .unset("fechaLimiteRespuesta");
        if (mecanico != null) {
            cambios.set("mecanicoId", mecanico.getId()).set("mecanicoNombre", mecanico.getNombre());
        }
        // Solo si sigue siendo de este taller y nadie la aceptó o reasignó entretanto.
        Criteria condicion = Criteria.where("tallerId").is(taller.getId()).and("aceptadaPorTaller").ne(true);
        Actor actor = actorTaller(taller);
        String fechaTexto = r.getFechaRevision().toString().replace('T', ' ');

        OrdenServicio actualizada;
        if (pagoRequerido) {
            LocalDateTime limitePago = ahora.plusMinutes(parametros.timeoutPagoDiagnosticoMin());
            if (limitePago.isAfter(r.getFechaRevision())) limitePago = r.getFechaRevision();
            cambios.set("pagoDiagnosticoEstado", EstadoOrden.PAGO_PENDIENTE)
                    .set("fechaLimitePagoDiagnostico", limitePago);
            actualizada = transiciones.registrar(orden, actor,
                    "Cita de revisión propuesta para " + fechaTexto + "; pendiente del pago del diagnóstico",
                    condicion, cambios);
        } else {
            cambios.set("pagoDiagnosticoEstado", EstadoOrden.PAGO_NO_REQUERIDO);
            actualizada = transiciones.transicionar(orden, EstadoOrden.EN_REVISION, actor,
                    "Cita de revisión agendada para " + fechaTexto, condicion, cambios);
        }
        avisos.citaRevision(actualizada, pagoRequerido);
        return toDTO(actualizada, Vista.TALLER);
    }

    @Override
    public SolicitudServicioDTO rechazarPorTaller(String correoTaller, String id, String motivo) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        if (!EstadoOrden.ASIGNADA_A_TALLER.equals(orden.getEstado()) || Boolean.TRUE.equals(orden.getAceptadaPorTaller())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden rechazar solicitudes sin aceptar");
        }
        String texto = "Rechazada por " + taller.getNombre()
                + (motivo != null && !motivo.isBlank() ? ": " + motivo.trim() : "");
        OrdenServicio resultado = asignacion.reasignar(orden, actorTaller(taller), texto);
        // El taller ya no tiene acceso a la solicitud: se devuelve solo lo básico.
        return SolicitudServicioDTO.builder().id(resultado.getId()).estado("REASIGNADA").build();
    }

    @Override
    public SolicitudServicioDTO asignarMecanico(String correoTaller, String id, String mecanicoId) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        if (!Boolean.TRUE.equals(orden.getAceptadaPorTaller()) || !EstadoOrden.ACTIVOS.contains(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero acepta la solicitud");
        }
        Mecanico mecanico = mecanicoActivo(taller, mecanicoId);
        if (mecanico == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un mecánico");
        }
        OrdenServicio actualizada = transiciones.registrar(orden, actorTaller(taller),
                "Mecánico asignado: " + mecanico.getNombre(),
                Criteria.where("tallerId").is(taller.getId()),
                new Update().set("mecanicoId", mecanico.getId()).set("mecanicoNombre", mecanico.getNombre()));
        return toDTO(actualizada, Vista.TALLER);
    }

    @Override
    public SolicitudServicioDTO registrarDiagnostico(String correoTaller, String id, DiagnosticoRequest r) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        boolean primeraVez = EstadoOrden.EN_REVISION.equals(orden.getEstado());
        if (!primeraVez && !EstadoOrden.DIAGNOSTICADA.equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El diagnóstico se registra con el vehículo en revisión (estado actual: " + orden.getEstado() + ")");
        }
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completa el diagnóstico");
        }
        String hallazgos = r.getHallazgos() != null ? r.getHallazgos().trim() : "";
        if (hallazgos.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe los hallazgos (mínimo 10 caracteres)");
        }
        if (hallazgos.length() > MAX_TEXTO_DIAGNOSTICO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los hallazgos son demasiado largos");
        }
        String urgencia = r.getUrgencia() != null ? r.getUrgencia().trim().toUpperCase() : "";
        if (!Diagnostico.URGENCIAS.contains(urgencia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la urgencia: BAJA, MEDIA, ALTA o CRITICA");
        }
        List<String> fallas = r.getFallas() != null
                ? r.getFallas().stream().filter(f -> f != null && !f.isBlank()).map(String::trim).toList()
                : List.of();
        if (fallas.size() > MAX_FALLAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_FALLAS + " fallas");
        }
        List<String> fotos = r.getFotos() != null
                ? r.getFotos().stream().filter(f -> f != null && f.startsWith("http")).toList()
                : List.of();
        if (fotos.size() > MAX_FOTOS_DIAGNOSTICO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_FOTOS_DIAGNOSTICO + " fotos");
        }

        // El diagnóstico queda a nombre de un mecánico: el de la solicitud o el que se indique ahora.
        Mecanico mecanico = r.getMecanicoId() != null && !r.getMecanicoId().isBlank()
                ? mecanicoActivo(taller, r.getMecanicoId()) : null;
        String mecanicoId = mecanico != null ? mecanico.getId() : orden.getMecanicoId();
        String mecanicoNombre = mecanico != null ? mecanico.getNombre() : orden.getMecanicoNombre();
        if (mecanicoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asigna el mecánico que hizo la revisión");
        }

        Diagnostico diagnostico = Diagnostico.builder()
                .hallazgos(hallazgos)
                .fallas(new ArrayList<>(fallas))
                .urgencia(urgencia)
                .observaciones(r.getObservaciones() != null ? r.getObservaciones().trim() : null)
                .fotos(new ArrayList<>(fotos))
                .mecanicoId(mecanicoId)
                .mecanicoNombre(mecanicoNombre)
                .fecha(LocalDateTime.now())
                .build();
        Update cambios = new Update()
                .set("diagnostico", diagnostico)
                .set("mecanicoId", mecanicoId)
                .set("mecanicoNombre", mecanicoNombre);

        // Los repuestos necesarios se guardan en la misma escritura que el diagnóstico.
        List<String> notasAdicionales = new ArrayList<>();
        if (r.getRepuestos() != null) {
            Seleccion seleccion = construirSeleccion(taller, orden, r.getRepuestos(), mecanicoId, mecanicoNombre);
            cambios.set("repuestos", seleccion.repuestos());
            notasAdicionales.add(notaSeleccion(seleccion.repuestos()));
            notasAdicionales.addAll(seleccion.confirmaciones());
        }

        Criteria condicion = Criteria.where("tallerId").is(taller.getId());
        Actor actor = actorTaller(taller);

        OrdenServicio actualizada = primeraVez
                ? transiciones.transicionar(orden, EstadoOrden.DIAGNOSTICADA, actor,
                        "Diagnóstico registrado por " + mecanicoNombre + " (urgencia " + urgencia + ")",
                        condicion, cambios, notasAdicionales)
                : transiciones.registrar(orden, actor, "Diagnóstico actualizado", condicion, cambios, notasAdicionales);
        return toDTO(actualizada, Vista.TALLER);
    }

    @Override
    public SolicitudServicioDTO seleccionarRepuestos(String correoTaller, String id, SeleccionRepuestosRequest r) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        if (!EstadoOrden.DIAGNOSTICADA.equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Los repuestos se eligen después del diagnóstico y antes de cotizar");
        }
        List<SeleccionRepuestosRequest.Item> items = r != null && r.getItems() != null ? r.getItems() : List.of();
        // Las confirmaciones quedan a nombre del mecánico responsable del diagnóstico.
        Seleccion seleccion = construirSeleccion(taller, orden, items, orden.getMecanicoId(), orden.getMecanicoNombre());

        OrdenServicio actualizada = transiciones.registrar(orden, actorTaller(taller), notaSeleccion(seleccion.repuestos()),
                Criteria.where("tallerId").is(taller.getId()),
                new Update().set("repuestos", seleccion.repuestos()),
                seleccion.confirmaciones());
        return toDTO(actualizada, Vista.TALLER);
    }

    /** Repuestos validados y notas de las confirmaciones nuevas para la línea de tiempo. */
    private record Seleccion(List<RepuestoSeleccionado> repuestos, List<String> confirmaciones) {
    }

    /**
     * Valida cada repuesto con las reglas del buscador: no se acepta lo NO_COMPATIBLE
     * ni lo agotado o con stock insuficiente que no sea por encargo. Lo POR_CONFIRMAR
     * exige confirmoCompatibilidad=true; la confirmación se guarda con el mecánico y la
     * fecha. Una confirmación anterior del mismo producto se conserva tal cual.
     */
    private Seleccion construirSeleccion(Taller taller, OrdenServicio orden, List<SeleccionRepuestosRequest.Item> items,
                                         String mecanicoId, String mecanicoNombre) {
        if (items.size() > MAX_REPUESTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_REPUESTOS + " repuestos por orden");
        }
        Vehiculo vehiculo = vehiculoService.obtenerEntidad(orden.getVehiculoId());
        Map<String, RepuestoSeleccionado> confirmadosAntes = (orden.getRepuestos() != null ? orden.getRepuestos() : List.<RepuestoSeleccionado>of())
                .stream()
                .filter(rep -> Boolean.TRUE.equals(rep.getCompatibilidadConfirmada()) && rep.getProductoId() != null)
                .collect(Collectors.toMap(RepuestoSeleccionado::getProductoId, rep -> rep, (a, b) -> a));

        List<RepuestoSeleccionado> seleccion = new ArrayList<>();
        List<String> confirmaciones = new ArrayList<>();
        Set<String> productosVistos = new HashSet<>();
        for (SeleccionRepuestosRequest.Item item : items) {
            if (item.getProductoId() == null || item.getProductoId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada repuesto debe tener un producto elegido");
            }
            if (!productosVistos.add(item.getProductoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un mismo producto aparece dos veces: ajusta la cantidad en una sola línea");
            }
            int cantidad = item.getCantidad() != null ? item.getCantidad() : 0;
            if (cantidad < 1 || cantidad > MAX_CANTIDAD) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre 1 y " + MAX_CANTIDAD);
            }
            Producto producto;
            try {
                producto = productoService.obtenerPorId(item.getProductoId());
            } catch (RuntimeException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uno de los productos elegidos ya no existe");
            }
            OfertaRepuestoDTO oferta = buscador.ofertaParaSeleccion(producto, cantidad, taller, vehiculo);

            String descripcion = item.getDescripcion() != null && !item.getDescripcion().isBlank()
                    ? item.getDescripcion().trim() : producto.getNombre();
            RepuestoSeleccionado.RepuestoSeleccionadoBuilder repuesto = RepuestoSeleccionado.builder()
                    .descripcion(descripcion)
                    .cantidad(cantidad)
                    .productoId(producto.getId())
                    .productoNombre(producto.getNombre())
                    .marca(producto.getMarca())
                    .referencia(producto.getReferencia())
                    .tiendaId(oferta.getTiendaId())
                    .tiendaNombre(oferta.getTiendaNombre())
                    .precioReferencia(oferta.getPrecio())
                    .disponibilidad(oferta.getDisponibilidad())
                    .diasEntrega(oferta.getDiasEntrega())
                    .distanciaKm(oferta.getDistanciaKm())
                    .compatibilidad(oferta.getCompatibilidad());

            if (oferta.isRequiereConfirmacion()) {
                if (!Boolean.TRUE.equals(item.getConfirmoCompatibilidad())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Confirma que \"" + producto.getNombre() + "\" es compatible con el vehículo: no tiene datos de compatibilidad");
                }
                if (mecanicoId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Asigna el mecánico responsable antes de confirmar compatibilidades");
                }
                RepuestoSeleccionado anterior = confirmadosAntes.get(producto.getId());
                if (anterior != null) {
                    repuesto.compatibilidadConfirmada(true)
                            .confirmadoPorMecanicoId(anterior.getConfirmadoPorMecanicoId())
                            .confirmadoPorMecanicoNombre(anterior.getConfirmadoPorMecanicoNombre())
                            .fechaConfirmacion(anterior.getFechaConfirmacion());
                } else {
                    repuesto.compatibilidadConfirmada(true)
                            .confirmadoPorMecanicoId(mecanicoId)
                            .confirmadoPorMecanicoNombre(mecanicoNombre)
                            .fechaConfirmacion(LocalDateTime.now());
                    confirmaciones.add("Compatibilidad confirmada por " + mecanicoNombre + ": "
                            + producto.getNombre() + " (" + oferta.getTiendaNombre() + ")");
                }
            }
            seleccion.add(repuesto.build());
        }
        return new Seleccion(seleccion, confirmaciones);
    }

    private static String notaSeleccion(List<RepuestoSeleccionado> seleccion) {
        if (seleccion.isEmpty()) return "Sin repuestos (solo mano de obra)";
        long tiendas = seleccion.stream().map(RepuestoSeleccionado::getTiendaId).distinct().count();
        return seleccion.size() + " repuesto(s) elegidos en " + tiendas + " tienda(s)";
    }

    // ─── Etapas finales ──────────────────────────────────────────────────────

    @Override
    public SolicitudServicioDTO iniciarInstalacion(String correoTaller, String id) {
        return avanzar(correoTaller, id, EstadoOrden.EN_INSTALACION, "Instalación iniciada",
                new Update().set("fechaInicioInstalacion", LocalDateTime.now()));
    }

    @Override
    public SolicitudServicioDTO marcarEnPruebas(String correoTaller, String id) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        if (!EstadoOrden.EN_INSTALACION.equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo una solicitud en instalación pasa a pruebas");
        }
        if (EstadoOrden.SUB_EN_PRUEBAS.equals(orden.getSubestado())) return toDTO(orden, Vista.TALLER);
        OrdenServicio actualizada = transiciones.registrar(orden, actorTaller(taller), conMecanico("En pruebas", orden),
                Criteria.where("tallerId").is(taller.getId()).and("subestado").ne(EstadoOrden.SUB_EN_PRUEBAS),
                new Update().set("subestado", EstadoOrden.SUB_EN_PRUEBAS));
        return toDTO(actualizada, Vista.TALLER);
    }

    @Override
    public SolicitudServicioDTO marcarLista(String correoTaller, String id) {
        SolicitudServicioDTO lista = avanzar(correoTaller, id, EstadoOrden.LISTA, "Vehículo listo para entregar",
                new Update().set("fechaLista", LocalDateTime.now()).unset("subestado"));
        ordenRepository.findById(id).ifPresent(avisos::vehiculoListo);
        return lista;
    }

    @Override
    public SolicitudServicioDTO marcarEntregada(String correoTaller, String id) {
        SolicitudServicioDTO entregada = avanzar(correoTaller, id, EstadoOrden.ENTREGADA, "Vehículo entregado al cliente",
                new Update().set("fechaEntrega", LocalDateTime.now()));
        // La línea del taller queda lista para liquidar (SIMULADO: se paga sola; MANUAL: la paga el ADMIN)
        liquidacion.ordenEntregada(id);
        return entregada;
    }

    /**
     * Mueve la orden con las transiciones existentes. Solo el taller asignado (y
     * habilitado): la escritura además exige que la orden siga siendo de ese taller.
     * La tabla de EstadoOrden decide desde qué estado se puede ir.
     */
    private SolicitudServicioDTO avanzar(String correoTaller, String id, String hacia, String nota, Update cambios) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = solicitudDelTaller(taller, id);
        OrdenServicio actualizada = transiciones.transicionar(orden, hacia, actorTaller(taller), conMecanico(nota, orden),
                Criteria.where("tallerId").is(taller.getId()), cambios);
        return toDTO(actualizada, Vista.TALLER);
    }

    private static String conMecanico(String nota, OrdenServicio orden) {
        return orden.getMecanicoNombre() != null ? nota + " · Mecánico: " + orden.getMecanicoNombre() : nota;
    }

    @Override
    public SolicitudServicioDTO confirmarRecepcion(String correoCliente, String id, Integer calificacion, String comentario) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        OrdenServicio orden = obtenerMarketplace(id);
        if (!cliente.getId().equals(orden.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no le pertenece");
        }
        if (!EstadoOrden.ENTREGADA.equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El taller aún no ha marcado la entrega");
        }
        if (Boolean.TRUE.equals(orden.getRecepcionConfirmada())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya confirmaste la recepción");
        }
        if (calificacion != null && (calificacion < 1 || calificacion > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La calificación va de 1 a 5");
        }
        String texto = comentario != null && !comentario.isBlank() ? comentario.trim() : null;
        if (texto != null && texto.length() > 500) texto = texto.substring(0, 500);

        Update cambios = new Update()
                .set("recepcionConfirmada", true)
                .set("fechaRecepcionCliente", LocalDateTime.now());
        if (calificacion != null) cambios.set("calificacionTaller", calificacion);
        if (texto != null) cambios.set("comentarioCalificacion", texto);
        String nota = "El cliente confirmó la recepción del vehículo"
                + (calificacion != null ? " · Calificación: " + calificacion + "/5" : "");
        OrdenServicio actualizada = transiciones.registrar(orden,
                new Actor(EstadoOrden.CLIENTE, cliente.getId(), cliente.getNombre()), nota,
                Criteria.where("usuarioId").is(cliente.getId()).and("recepcionConfirmada").ne(true), cambios);
        // La nota ya quedó en la orden; si el recálculo falla, lo corrige la siguiente calificación o el de arranque
        if (calificacion != null) {
            try {
                calificaciones.recalcular(orden.getTallerId());
            } catch (RuntimeException e) {
                log.warn("No se pudo recalcular la calificación del taller {}: {}", orden.getTallerId(), e.getMessage());
            }
        }
        return toDTO(actualizada, Vista.CLIENTE);
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────

    @Override
    public List<SolicitudServicioDTO> listarAdmin(String estado) {
        List<OrdenServicio> ordenes = estado != null && !estado.isBlank()
                ? ordenRepository.findByFlujoAndEstadoOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, estado.trim().toUpperCase())
                : ordenRepository.findByFlujoOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE);
        return toDTOs(ordenes, Vista.ADMIN);
    }

    @Override
    public SolicitudServicioDTO reasignarPorAdmin(String correoAdmin, String id, String motivo) {
        Usuario admin = usuarioService.obtenerPorCorreo(correoAdmin);
        OrdenServicio orden = obtenerMarketplace(id);
        if (!EstadoOrden.ASIGNADA_A_TALLER.equals(orden.getEstado()) || Boolean.TRUE.equals(orden.getAceptadaPorTaller())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se reasignan solicitudes que el taller aún no ha aceptado");
        }
        String texto = "Reasignada por DriveMaster" + (motivo != null && !motivo.isBlank() ? ": " + motivo.trim() : "");
        OrdenServicio resultado = asignacion.reasignar(orden,
                new Actor(EstadoOrden.ADMIN, admin.getId(), admin.getNombre()), texto);
        return toDTO(resultado, Vista.ADMIN);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private OrdenServicio obtenerMarketplace(String id) {
        OrdenServicio orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!EstadoOrden.FLUJO_MARKETPLACE.equals(orden.getFlujo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        return orden;
    }

    private OrdenServicio solicitudDelTaller(Taller taller, String id) {
        OrdenServicio orden = obtenerMarketplace(id);
        if (!taller.getId().equals(orden.getTallerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no está asignada a tu taller");
        }
        return orden;
    }

    private Taller tallerOperativo(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion()) || !Boolean.TRUE.equals(taller.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller no está habilitado para operar");
        }
        return taller;
    }

    private Mecanico mecanicoActivo(Taller taller, String mecanicoId) {
        if (mecanicoId == null || mecanicoId.isBlank()) return null;
        return (taller.getMecanicos() != null ? taller.getMecanicos() : List.<Mecanico>of()).stream()
                .filter(m -> mecanicoId.equals(m.getId()) && !Boolean.FALSE.equals(m.getActivo()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mecánico no válido o inactivo"));
    }

    private Actor actorTaller(Taller taller) {
        return new Actor(EstadoOrden.TALLER, taller.getId(), taller.getNombre());
    }

    private enum Vista { CLIENTE, TALLER, ADMIN }

    private static final int MAX_DIRECCION = 200;

    /** Texto plano de una línea y con largo acotado: viene de un geocodificador externo, a través del navegador. */
    static String limpiarDireccion(String direccion) {
        if (direccion == null) return null;
        String limpia = direccion.replaceAll("[<>]", " ").replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (limpia.isEmpty()) return null;
        return limpia.length() > MAX_DIRECCION ? limpia.substring(0, MAX_DIRECCION).trim() : limpia;
    }

    /**
     * Lo que toDTO busca fuera de la orden (placa del vehículo, datos del taller y del cliente), cargado para varias
     * órdenes a la vez: una consulta por colección en lugar de una por orden.
     */
    private record Relacionados(Map<String, Vehiculo> vehiculos, Map<String, Taller> talleres, Map<String, Usuario> clientes) {}

    private Relacionados relacionados(List<OrdenServicio> ordenes, Vista vista) {
        Set<String> vehiculoIds = new HashSet<>();
        Set<String> tallerIds = new HashSet<>();
        Set<String> clienteIds = new HashSet<>();
        for (OrdenServicio o : ordenes) {
            if (o.getVehiculoId() != null) vehiculoIds.add(o.getVehiculoId());
            if (vista != Vista.TALLER && o.getTallerId() != null) tallerIds.add(o.getTallerId());
            if (vista != Vista.CLIENTE && o.getUsuarioId() != null) clienteIds.add(o.getUsuarioId());
        }
        Map<String, Vehiculo> vehiculos = new HashMap<>();
        Map<String, Taller> talleres = new HashMap<>();
        Map<String, Usuario> clientes = new HashMap<>();
        // Si una búsqueda falla, la orden se muestra igual sin ese dato (como cuando el vehículo o el cliente ya no existen)
        try {
            vehiculoService.obtenerEntidades(vehiculoIds).forEach(v -> vehiculos.put(v.getId(), v));
        } catch (Exception ignorado) { /* sin placas */ }
        if (!tallerIds.isEmpty()) tallerRepository.findAllById(tallerIds).forEach(t -> talleres.put(t.getId(), t));
        try {
            usuarioService.obtenerPorIds(clienteIds).forEach(u -> clientes.put(u.getId(), u));
        } catch (Exception ignorado) { /* sin datos del cliente */ }
        return new Relacionados(vehiculos, talleres, clientes);
    }

    private List<SolicitudServicioDTO> toDTOs(List<OrdenServicio> ordenes, Vista vista) {
        Relacionados r = relacionados(ordenes, vista);
        return ordenes.stream().map(o -> toDTO(o, vista, r)).toList();
    }

    private SolicitudServicioDTO toDTO(OrdenServicio o, Vista vista) {
        return toDTO(o, vista, relacionados(List.of(o), vista));
    }

    private SolicitudServicioDTO toDTO(OrdenServicio o, Vista vista, Relacionados r) {
        SolicitudServicioDTO.SolicitudServicioDTOBuilder b = SolicitudServicioDTO.builder()
                .id(o.getId())
                .estado(o.getEstado())
                .vehiculoId(o.getVehiculoId())
                .vehiculoDescripcion(o.getVehiculoDescripcion())
                .vehiculoImagenUrl(o.getVehiculoImagenUrl())
                .sintomas(o.getSintomas())
                .fotos(o.getFotos())
                .ciudad(o.getCiudad())
                .barrio(o.getBarrio())
                .preferenciaHorario(o.getPreferenciaHorario())
                .fechaCreacion(o.getFechaCreacion())
                .tallerId(o.getTallerId())
                .tallerNombre(o.getTallerNombre())
                .distanciaTallerKm(o.getDistanciaTallerKm())
                .fechaAsignacion(o.getFechaAsignacion())
                .fechaLimiteRespuesta(o.getFechaLimiteRespuesta())
                .tallerElegidoId(o.getTallerElegidoId())
                .tallerElegidoNombre(o.getTallerElegidoNombre())
                .tallerElegidoNoAcepto(o.getTallerElegidoId() != null && o.getTalleresDescartados() != null
                        && o.getTalleresDescartados().contains(o.getTallerElegidoId()))
                .aceptadaPorTaller(o.getAceptadaPorTaller())
                .fechaRevision(o.getFechaProgramada())
                .mecanicoId(o.getMecanicoId())
                .mecanicoNombre(o.getMecanicoNombre())
                .tarifaDiagnostico(o.getTarifaDiagnostico())
                .pagoDiagnosticoEstado(o.getPagoDiagnosticoEstado())
                .fechaLimitePagoDiagnostico(o.getFechaLimitePagoDiagnostico())
                .motivoCierre(o.getMotivoCierre())
                .diagnostico(o.getDiagnostico())
                .repuestos(o.getRepuestos())
                .cotizacionId(o.getCotizacionId())
                .subestado(o.getSubestado())
                .fechaInicioInstalacion(o.getFechaInicioInstalacion())
                .fechaLista(o.getFechaLista())
                .fechaEntrega(o.getFechaEntrega())
                .recepcionConfirmada(o.getRecepcionConfirmada())
                .fechaRecepcionCliente(o.getFechaRecepcionCliente())
                .calificacionTaller(o.getCalificacionTaller())
                .comentarioCalificacion(o.getComentarioCalificacion())
                .eventos(o.getEventos());

        // Vehículo eliminado: sin placa, se muestra la descripción guardada
        Vehiculo v = r.vehiculos().get(o.getVehiculoId());
        if (v != null) b.vehiculoPlaca(v.getPlaca());

        // El cliente ve su propio punto en el mapa y la dirección, el teléfono y la ubicación del
        // taller asignado (para la ruta y el botón "Cómo llegar").
        if (vista != Vista.TALLER) {
            b.direccion(o.getDireccion()).latitud(o.getLatitud()).longitud(o.getLongitud());
            Taller t = o.getTallerId() == null ? null : r.talleres().get(o.getTallerId());
            if (t != null) {
                b.tallerDireccion(t.getDireccion()).tallerTelefono(t.getTelefono())
                        .tallerLatitud(t.getLatitud()).tallerLongitud(t.getLongitud());
            }
        }
        // El taller asignado y el ADMIN ven el contacto del cliente.
        if (vista != Vista.CLIENTE) {
            Usuario c = r.clientes().get(o.getUsuarioId());
            if (c != null) b.clienteNombre(c.getNombre()).clienteTelefono(c.getTelefono()).clienteCorreo(c.getCorreo());
        }
        return b.build();
    }
}
