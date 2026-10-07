package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AceptarSolicitudRequest;
import com.drivemaster.drivemaster.dto.CrearSolicitudServicioRequest;
import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SolicitudServicioServiceImpl implements SolicitudServicioService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudServicioServiceImpl.class);

    private static final int MAX_FOTOS = 5;
    private static final int MAX_SINTOMAS = 1500;
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

    public SolicitudServicioServiceImpl(OrdenServicioRepository ordenRepository,
                                        TallerRepository tallerRepository,
                                        VehiculoService vehiculoService,
                                        UsuarioService usuarioService,
                                        TallerService tallerService,
                                        CloudinaryService cloudinaryService,
                                        TransicionOrdenService transiciones,
                                        AsignacionTallerService asignacion,
                                        ParametrosMarketplace parametros,
                                        AvisosSolicitudServicio avisos) {
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
                .latitud(r.getLatitud())
                .longitud(r.getLongitud())
                .preferenciaHorario(r.getPreferenciaHorario())
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
        return ordenRepository.findByFlujoAndUsuarioIdOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, cliente.getId())
                .stream().map(o -> toDTO(o, Vista.CLIENTE)).toList();
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
        return ordenRepository.findByFlujoAndTallerIdOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, taller.getId())
                .stream().map(o -> toDTO(o, Vista.TALLER)).toList();
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

    // ─── ADMIN ───────────────────────────────────────────────────────────────

    @Override
    public List<SolicitudServicioDTO> listarAdmin(String estado) {
        List<OrdenServicio> ordenes = estado != null && !estado.isBlank()
                ? ordenRepository.findByFlujoAndEstadoOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, estado.trim().toUpperCase())
                : ordenRepository.findByFlujoOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE);
        return ordenes.stream().map(o -> toDTO(o, Vista.ADMIN)).toList();
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

    private SolicitudServicioDTO toDTO(OrdenServicio o, Vista vista) {
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
                .aceptadaPorTaller(o.getAceptadaPorTaller())
                .fechaRevision(o.getFechaProgramada())
                .mecanicoId(o.getMecanicoId())
                .mecanicoNombre(o.getMecanicoNombre())
                .tarifaDiagnostico(o.getTarifaDiagnostico())
                .pagoDiagnosticoEstado(o.getPagoDiagnosticoEstado())
                .fechaLimitePagoDiagnostico(o.getFechaLimitePagoDiagnostico())
                .motivoCierre(o.getMotivoCierre())
                .eventos(o.getEventos());

        try {
            Vehiculo v = vehiculoService.obtenerEntidad(o.getVehiculoId());
            b.vehiculoPlaca(v.getPlaca());
        } catch (Exception ignorado) {
            // vehículo eliminado: se muestra la descripción guardada
        }

        // El cliente ve la dirección y el teléfono del taller asignado.
        if (vista != Vista.TALLER && o.getTallerId() != null) {
            tallerRepository.findById(o.getTallerId()).ifPresent(t ->
                    b.tallerDireccion(t.getDireccion()).tallerTelefono(t.getTelefono()));
        }
        // El taller asignado y el ADMIN ven el contacto del cliente.
        if (vista != Vista.CLIENTE) {
            try {
                Usuario c = usuarioService.obtenerPorId(o.getUsuarioId());
                b.clienteNombre(c.getNombre()).clienteTelefono(c.getTelefono()).clienteCorreo(c.getCorreo());
            } catch (Exception ignorado) {
                // cliente eliminado
            }
        }
        return b.build();
    }
}
