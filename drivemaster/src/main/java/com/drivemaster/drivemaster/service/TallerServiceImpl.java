package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TallerMapaDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.util.Coordenadas;
import com.drivemaster.drivemaster.util.HaversineUtil;
import com.drivemaster.drivemaster.util.IvaTaller;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TallerServiceImpl implements TallerService {

    private final TallerRepository tallerRepository;
    private final UsuarioRepository usuarioRepository;
    private final CuentaNegocioService cuentaNegocioService;
    private final SuscripcionService suscripcionService;

    public TallerServiceImpl(TallerRepository tallerRepository,
                              UsuarioRepository usuarioRepository,
                              CuentaNegocioService cuentaNegocioService,
                              SuscripcionService suscripcionService) {
        this.tallerRepository = tallerRepository;
        this.usuarioRepository = usuarioRepository;
        this.cuentaNegocioService = cuentaNegocioService;
        this.suscripcionService = suscripcionService;
    }

    /** Alta directa por el ADMIN: el taller queda aprobado y activo de inmediato. */
    @Override
    public TallerDTO crear(CrearTallerRequest request) {
        Coordenadas.validar(request.getLatitud(), request.getLongitud(), true);
        String usuarioId = request.getUsuarioId();
        Usuario cuentaNueva = null;

        if (usuarioId == null || usuarioId.isBlank()) {
            if (request.getCorreoUsuario() == null || request.getCorreoUsuario().isBlank()
                    || request.getPasswordUsuario() == null || request.getPasswordUsuario().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe indicar correoUsuario y passwordUsuario para crear el usuario del taller");
            }
            cuentaNueva = cuentaNegocioService.crearUsuario(request.getNombre(),
                    request.getCorreoUsuario(), request.getPasswordUsuario(),
                    request.getTelefono(), request.getCiudad(), RolPermisos.TALLER);
            usuarioId = cuentaNueva.getId();
        } else {
            usuarioId = usuarioId.trim();
            validarCuentaParaTallerNuevo(usuarioId);
        }

        Taller taller = Taller.builder()
                .usuarioId(usuarioId)
                .nombre(request.getNombre())
                .ciudad(request.getCiudad())
                .barrio(request.getBarrio())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .latitud(request.getLatitud())
                .longitud(request.getLongitud())
                .especialidades(request.getEspecialidades() != null ? request.getEspecialidades() : new ArrayList<>())
                // Sin calificaciones hasta que un cliente califique (el rating del pedido se ignora)
                .calificaciones(0)
                .precioManoObra(request.getPrecioManoObra())
                .tarifaHora(request.getTarifaHora())
                .horario(request.getHorario())
                .estadoAprobacion(EstadoAprobacion.APROBADO)
                .fechaRegistro(LocalDateTime.now())
                .fechaAprobacion(LocalDateTime.now())
                .activo(true)
                .build();

        // Si se creó la cuenta aquí y falla guardar el taller, la cuenta se borra (ver CuentaNegocioService)
        Taller guardado = cuentaNueva != null
                ? cuentaNegocioService.conCuentaNueva(cuentaNueva, () -> tallerRepository.save(taller))
                : tallerRepository.save(taller);
        return toDTOCompleto(guardado);
    }

    /**
     * Un usuarioId que llega en el alta debe ser una cuenta TALLER existente y sin taller: si no, el taller
     * quedaría apuntando a nadie, a una cuenta de otro tipo o compartiendo dueño con otro taller.
     */
    private void validarCuentaParaTallerNuevo(String usuarioId) {
        Usuario cuenta = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario indicado no existe"));
        if (!RolPermisos.TALLER.equals(cuenta.getRol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario indicado no es una cuenta de taller");
        }
        if (tallerRepository.findByUsuarioId(usuarioId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese usuario ya tiene un taller");
        }
    }

    @Override
    public TallerDTO actualizar(String id, CrearTallerRequest request) {
        // Los talleres anteriores al mapa pueden seguir sin ubicación; si se envía, debe ser válida
        Coordenadas.validar(request.getLatitud(), request.getLongitud(), false);
        Taller taller = obtenerEntidad(id);
        taller.setNombre(request.getNombre());
        taller.setCiudad(request.getCiudad());
        taller.setDireccion(request.getDireccion());
        taller.setLatitud(request.getLatitud());
        taller.setLongitud(request.getLongitud());
        if (request.getEspecialidades() != null) taller.setEspecialidades(request.getEspecialidades());
        if (request.getPrecioManoObra() != null) taller.setPrecioManoObra(request.getPrecioManoObra());
        if (request.getBarrio() != null) taller.setBarrio(request.getBarrio());
        if (request.getTelefono() != null) taller.setTelefono(request.getTelefono());
        if (request.getTarifaHora() != null) taller.setTarifaHora(request.getTarifaHora());
        if (request.getHorario() != null) taller.setHorario(request.getHorario());

        taller = tallerRepository.save(taller);
        return toDTOCompleto(taller);
    }

    @Override
    public TallerDTO activarDesactivar(String id, boolean activo) {
        Taller taller = obtenerEntidad(id);
        if (activo && !EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero hay que aprobar el taller");
        }
        taller.setActivo(activo);
        taller = tallerRepository.save(taller);
        return toDTOCompleto(taller);
    }

    @Override
    public List<TallerDTO> listarTodos(String estadoAprobacion) {
        List<Taller> talleres = (estadoAprobacion != null && !estadoAprobacion.isBlank())
                ? tallerRepository.findByEstadoAprobacion(estadoAprobacion.trim().toUpperCase())
                : tallerRepository.findAll();
        return talleres.stream()
                .map(this::toDTOCompleto)
                .collect(Collectors.toList());
    }

    /** Listado público: solo talleres aprobados y activos, sin datos internos. */
    @Override
    public List<TallerDTO> listarActivos(String ciudad, Double lat, Double lng) {
        List<Taller> talleres = (ciudad != null && !ciudad.isBlank())
                ? tallerRepository.findByCiudadIgnoreCaseAndActivoTrue(ciudad)
                : tallerRepository.findByActivoTrue();

        return talleres.stream()
                .filter(t -> EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()))
                .map(t -> toDTO(t, lat, lng))
                .collect(Collectors.toList());
    }

    /**
     * Talleres que el cliente puede elegir al pedir un diagnóstico: aprobados y activos. Los que no tienen
     * ubicación llegan con latitud y longitud en null: salen en la lista, pero no en el mapa.
     */
    @Override
    public List<TallerMapaDTO> listarParaMapa() {
        return tallerRepository.findByActivoTrue().stream()
                .filter(t -> EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()))
                .map(TallerMapaDTO::de)
                .toList();
    }

    @Override
    public Taller obtenerEntidad(String id) {
        return tallerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taller no encontrado"));
    }

    @Override
    public Taller obtenerPorUsuario(String correoUsuario) {
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        Optional<Taller> taller = tallerRepository.findByUsuarioId(usuario.getId());
        return taller.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Este usuario no tiene un taller asociado"));
    }

    // ─── Registro y aprobación ──────────────────────────────

    /** Registro público: el taller queda PENDIENTE e inactivo hasta que el ADMIN lo apruebe. */
    @Override
    public TallerDTO registrar(RegistroNegocioRequest r) {
        cuentaNegocioService.validarRegistro(r);
        Coordenadas.validar(r.getLatitud(), r.getLongitud(), true);

        Usuario usuario = cuentaNegocioService.crearUsuario(
                r.getNombreContacto() != null && !r.getNombreContacto().isBlank() ? r.getNombreContacto() : r.getNombreNegocio(),
                r.getCorreo(), r.getPassword(), r.getTelefono(), r.getCiudad(), RolPermisos.TALLER);

        Taller taller = Taller.builder()
                .usuarioId(usuario.getId())
                .nombre(r.getNombreNegocio().trim())
                .ciudad(r.getCiudad())
                .barrio(r.getBarrio())
                .direccion(r.getDireccion())
                .telefono(r.getTelefono())
                .latitud(r.getLatitud())
                .longitud(r.getLongitud())
                .especialidades(r.getEspecialidades() != null ? r.getEspecialidades() : new ArrayList<>())
                .precioManoObra(r.getPrecioManoObra())
                .tarifaHora(r.getTarifaHora())
                .horario(r.getHorario())
                .calificaciones(0)
                .estadoAprobacion(EstadoAprobacion.PENDIENTE)
                .fechaRegistro(LocalDateTime.now())
                .activo(false)
                .build();

        // Si falla guardar el taller, la cuenta recién creada se borra (ver CuentaNegocioService)
        return toDTOCompleto(cuentaNegocioService.conCuentaNueva(usuario, () -> tallerRepository.save(taller)));
    }

    @Override
    public TallerDTO resolverAprobacion(String id, AprobacionRequest request, String correoAdmin) {
        Taller taller = obtenerEntidad(id);
        cuentaNegocioService.validarMotivoRechazo(request.getAprobado(), request.getMotivo());
        boolean aprobado = request.getAprobado();

        if (aprobado) {
            // Primero la suscripción: si el plan no es válido, el taller no queda aprobado a medias.
            suscripcionService.activar(SuscripcionService.ACTOR_TALLER, taller.getId(),
                    request.getPlanCodigo(), correoAdmin);
            taller.setEstadoAprobacion(EstadoAprobacion.APROBADO);
            taller.setMotivoRechazo(null);
            taller.setActivo(true);
            taller.setFechaAprobacion(LocalDateTime.now());
        } else {
            taller.setEstadoAprobacion(EstadoAprobacion.RECHAZADO);
            taller.setMotivoRechazo(request.getMotivo().trim());
            taller.setActivo(false);
        }

        taller = tallerRepository.save(taller);
        cuentaNegocioService.notificarResultado(taller.getUsuarioId(), taller.getNombre(), aprobado, taller.getMotivoRechazo());
        return toDTOCompleto(taller);
    }

    @Override
    public TallerDTO miTaller(String correoTaller) {
        return toDTOCompleto(obtenerPorUsuario(correoTaller));
    }

    // ─── Mecánicos ───────────────────────────────────────────

    @Override
    public List<Mecanico> listarMecanicos(String correoTaller) {
        return mecanicosDe(obtenerPorUsuario(correoTaller));
    }

    @Override
    public Mecanico agregarMecanico(String correoTaller, Mecanico datos) {
        Taller taller = obtenerPorUsuario(correoTaller);
        validarNombreMecanico(datos);

        Mecanico mecanico = Mecanico.builder()
                .id(UUID.randomUUID().toString())
                .nombre(datos.getNombre().trim())
                .especialidad(datos.getEspecialidad())
                .telefono(datos.getTelefono())
                .activo(true)
                .build();

        List<Mecanico> mecanicos = mecanicosDe(taller);
        mecanicos.add(mecanico);
        taller.setMecanicos(mecanicos);
        tallerRepository.save(taller);
        return mecanico;
    }

    @Override
    public Mecanico actualizarMecanico(String correoTaller, String mecanicoId, Mecanico datos) {
        Taller taller = obtenerPorUsuario(correoTaller);
        validarNombreMecanico(datos);
        Mecanico mecanico = buscarMecanico(taller, mecanicoId);
        mecanico.setNombre(datos.getNombre().trim());
        mecanico.setEspecialidad(datos.getEspecialidad());
        mecanico.setTelefono(datos.getTelefono());
        tallerRepository.save(taller);
        return mecanico;
    }

    /** Los mecánicos no se borran (quedan en el historial de las órdenes): se desactivan. */
    @Override
    public Mecanico cambiarEstadoMecanico(String correoTaller, String mecanicoId, boolean activo) {
        Taller taller = obtenerPorUsuario(correoTaller);
        Mecanico mecanico = buscarMecanico(taller, mecanicoId);
        mecanico.setActivo(activo);
        tallerRepository.save(taller);
        return mecanico;
    }

    private List<Mecanico> mecanicosDe(Taller taller) {
        if (taller.getMecanicos() == null) taller.setMecanicos(new ArrayList<>());
        return taller.getMecanicos();
    }

    private Mecanico buscarMecanico(Taller taller, String mecanicoId) {
        return mecanicosDe(taller).stream()
                .filter(m -> m.getId() != null && m.getId().equals(mecanicoId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mecánico no encontrado"));
    }

    private void validarNombreMecanico(Mecanico datos) {
        if (datos == null || datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del mecánico es obligatorio");
        }
    }

    // ─── DTO ─────────────────────────────────────────────────

    /** Vista pública (clientes): sin estado de revisión, mecánicos ni suscripción. */
    private TallerDTO toDTO(Taller t, Double lat, Double lng) {
        Double distancia = null;
        if (lat != null && lng != null && t.getLatitud() != null && t.getLongitud() != null) {
            distancia = HaversineUtil.calcularDistanciaKm(lat, lng, t.getLatitud(), t.getLongitud());
        }
        return baseDTO(t).distanciaKm(distancia).build();
    }

    /** Vista para el dueño del taller y el ADMIN. */
    private TallerDTO toDTOCompleto(Taller t) {
        Suscripcion plan = suscripcionService.vigente(SuscripcionService.ACTOR_TALLER, t.getId()).orElse(null);
        return baseDTO(t)
                .estadoAprobacion(t.getEstadoAprobacion())
                .motivoRechazo(t.getMotivoRechazo())
                .fechaRegistro(t.getFechaRegistro())
                .fechaAprobacion(t.getFechaAprobacion())
                .responsableIva(IvaTaller.esResponsable(t))
                .correoUsuario(cuentaNegocioService.correoDe(t.getUsuarioId()))
                .mecanicos(mecanicosDe(t))
                .planActual(plan != null ? plan.getPlanCodigo() : null)
                .planVence(plan != null ? plan.getFechaFin() : null)
                .build();
    }

    private TallerDTO.TallerDTOBuilder baseDTO(Taller t) {
        return TallerDTO.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .ciudad(t.getCiudad())
                .barrio(t.getBarrio())
                .direccion(t.getDireccion())
                .telefono(t.getTelefono())
                .latitud(t.getLatitud())
                .longitud(t.getLongitud())
                .especialidades(t.getEspecialidades())
                .rating(t.getRating())
                .calificaciones(t.getCalificaciones() != null ? t.getCalificaciones() : 0)
                .precioManoObra(t.getPrecioManoObra())
                .tarifaHora(t.getTarifaHora())
                .horario(t.getHorario())
                .logoUrl(t.getLogoUrl())
                .activo(t.getActivo());
    }
}
