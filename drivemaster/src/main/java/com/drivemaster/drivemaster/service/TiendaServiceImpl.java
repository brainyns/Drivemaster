package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.Suscripcion;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.util.Coordenadas;
import com.drivemaster.drivemaster.util.TelefonoColombia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TiendaServiceImpl implements TiendaService {

    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CuentaNegocioService cuentaNegocioService;
    private final SuscripcionService suscripcionService;

    public TiendaServiceImpl(TiendaRepository tiendaRepository,
                             UsuarioRepository usuarioRepository,
                             CuentaNegocioService cuentaNegocioService,
                             SuscripcionService suscripcionService) {
        this.tiendaRepository = tiendaRepository;
        this.usuarioRepository = usuarioRepository;
        this.cuentaNegocioService = cuentaNegocioService;
        this.suscripcionService = suscripcionService;
    }

    @Override
    public TiendaDTO registrar(RegistroNegocioRequest r) {
        cuentaNegocioService.validarRegistro(r);
        cuentaNegocioService.validarSinDuplicados(CuentaNegocioService.TIENDA, r);
        // Una tienda vende al público (retiro en su local): el horario es tan obligatorio como la dirección
        if (r.getHorario() == null || r.getHorario().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario de atención de la tienda es obligatorio");
        }
        validarCostoEnvio(r);

        Usuario usuario = cuentaNegocioService.crearUsuario(
                r.getNombreContacto() != null && !r.getNombreContacto().isBlank() ? r.getNombreContacto() : r.getNombreNegocio(),
                r.getCorreo(), r.getPassword(), r.getTelefono(), r.getCiudad(), RolPermisos.TIENDA);

        Tienda tienda = Tienda.builder()
                .usuarioId(usuario.getId())
                .nombre(r.getNombreNegocio().trim())
                .nit(r.getNit())
                .telefono(r.getTelefono())
                .correoContacto(usuario.getCorreo())
                .ciudad(r.getCiudad())
                .barrio(r.getBarrio())
                .direccion(r.getDireccion())
                .latitud(r.getLatitud())
                .longitud(r.getLongitud())
                .horario(r.getHorario().trim())
                .costoEnvio(costoEnvioDe(r))
                .estadoAprobacion(EstadoAprobacion.PENDIENTE)
                .activo(false)
                .fechaRegistro(LocalDateTime.now())
                .build();

        // Si falla guardar la tienda, la cuenta recién creada se borra (ver CuentaNegocioService)
        return toDTO(cuentaNegocioService.conCuentaNueva(usuario, () -> tiendaRepository.save(tienda)));
    }

    @Override
    public List<TiendaDTO> listar(String estadoAprobacion) {
        List<Tienda> tiendas = (estadoAprobacion != null && !estadoAprobacion.isBlank())
                ? tiendaRepository.findByEstadoAprobacion(estadoAprobacion.trim().toUpperCase())
                : tiendaRepository.findAll();
        return tiendas.stream().map(this::toDTO).toList();
    }

    @Override
    public TiendaDTO obtener(String id) {
        return toDTO(obtenerEntidad(id));
    }

    @Override
    public TiendaDTO resolverAprobacion(String id, AprobacionRequest request, String correoAdmin) {
        Tienda tienda = obtenerEntidad(id);
        cuentaNegocioService.validarMotivoRechazo(request.getAprobado(), request.getMotivo());
        boolean aprobado = request.getAprobado();

        if (aprobado) {
            // Primero la suscripción (si están en uso): si el plan no es válido, no queda aprobada a medias
            if (suscripcionService.habilitadas()) {
                suscripcionService.activar(SuscripcionService.ACTOR_TIENDA, tienda.getId(), request.getPlanCodigo(), correoAdmin);
            }
            tienda.setEstadoAprobacion(EstadoAprobacion.APROBADO);
            tienda.setMotivoRechazo(null);
            tienda.setActivo(true);
            tienda.setFechaAprobacion(LocalDateTime.now());
        } else {
            tienda.setEstadoAprobacion(EstadoAprobacion.RECHAZADO);
            tienda.setMotivoRechazo(request.getMotivo().trim());
            tienda.setActivo(false);
        }

        tienda = tiendaRepository.save(tienda);
        cuentaNegocioService.notificarResultado(tienda.getUsuarioId(), tienda.getNombre(), aprobado, tienda.getMotivoRechazo());
        return toDTO(tienda);
    }

    @Override
    public TiendaDTO activarDesactivar(String id, boolean activo) {
        Tienda tienda = obtenerEntidad(id);
        if (activo && !EstadoAprobacion.estaAprobado(tienda.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero hay que aprobar la tienda");
        }
        tienda.setActivo(activo);
        return toDTO(tiendaRepository.save(tienda));
    }

    @Override
    public TiendaDTO miTienda(String correoUsuario) {
        return toDTO(obtenerPorUsuario(correoUsuario));
    }

    /** El dueño actualiza los datos de contacto y ubicación. Nombre y NIT los cambia el ADMIN. */
    @Override
    public TiendaDTO actualizarMiTienda(String correoUsuario, RegistroNegocioRequest r) {
        return toDTO(tiendaRepository.save(aplicarDatos(obtenerPorUsuario(correoUsuario), r)));
    }

    @Override
    public TiendaDTO actualizarTelefono(String correoUsuario, String telefono) {
        Tienda tienda = obtenerPorUsuario(correoUsuario);
        tienda.setTelefono(TelefonoColombia.validar(telefono));
        return toDTO(tiendaRepository.save(tienda));
    }

    @Override
    public TiendaDTO guardarHorario(String correoUsuario, java.util.List<com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO> franjas) {
        Tienda tienda = obtenerPorUsuario(correoUsuario);
        var porDia = com.drivemaster.drivemaster.util.HorarioSemanal.validar(franjas);
        if (porDia.values().stream().allMatch(java.util.List::isEmpty)) {
            // El horario es obligatorio para vender: una semana toda cerrada es como no tenerlo
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Abre al menos un día: el cliente necesita saber cuándo pasar a retirar");
        }
        tienda.setHorarioSemanal(com.drivemaster.drivemaster.util.HorarioSemanal.semanaCompleta(porDia));
        // El texto sigue llenándose: lo leen el catálogo, los pedidos con retiro (tiendaHorario) y tieneDatosCompletos
        tienda.setHorario(com.drivemaster.drivemaster.util.HorarioSemanal.resumen(porDia));
        return toDTO(tiendaRepository.save(tienda));
    }

    private Tienda aplicarDatos(Tienda tienda, RegistroNegocioRequest r) {
        // El teléfono tiene su propio endpoint; si llega por aquí, con la misma validación
        if (r.getTelefono() != null) tienda.setTelefono(TelefonoColombia.validar(r.getTelefono()));
        if (r.getCiudad() != null && !r.getCiudad().isBlank()) tienda.setCiudad(r.getCiudad());
        if (r.getBarrio() != null) tienda.setBarrio(r.getBarrio());
        if (r.getDireccion() != null && !r.getDireccion().isBlank()) tienda.setDireccion(r.getDireccion());
        if (r.getLatitud() != null || r.getLongitud() != null) {
            Coordenadas.validar(r.getLatitud(), r.getLongitud(), false);
            tienda.setLatitud(r.getLatitud());
            tienda.setLongitud(r.getLongitud());
        }
        if (r.getHorario() != null && !r.getHorario().isBlank()) tienda.setHorario(r.getHorario().trim());
        // Envío: ofreceEnvio=false lo quita; ofreceEnvio=true (o solo costoEnvio) fija el costo fijo
        if (r.getOfreceEnvio() != null || r.getCostoEnvio() != null) {
            validarCostoEnvio(r);
            tienda.setCostoEnvio(costoEnvioDe(r));
        }
        return tienda;
    }

    @Override
    public Tienda obtenerOperativaPorUsuario(String correoUsuario) {
        Tienda tienda = obtenerPorUsuario(correoUsuario);
        if (!EstadoAprobacion.estaAprobado(tienda.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu tienda aún no ha sido aprobada por DriveMaster");
        }
        if (!Boolean.TRUE.equals(tienda.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu tienda está desactivada");
        }
        return tienda;
    }

    @Override
    public java.util.List<Tienda> listarVisibles() {
        return tiendaRepository.findAll().stream().filter(CatalogoService::tiendaVisible).toList();
    }

    @Override
    public Tienda obtenerEntidad(String id) {
        return tiendaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
    }

    @Override
    public Tienda obtenerPorUsuario(String correoUsuario) {
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return tiendaRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Este usuario no tiene una tienda asociada"));
    }


    /** El costo de envío es un valor fijo en pesos: no negativo y razonable. */
    private static void validarCostoEnvio(RegistroNegocioRequest r) {
        if (Boolean.TRUE.equals(r.getOfreceEnvio()) && r.getCostoEnvio() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el costo de envío de la tienda");
        }
        if (r.getCostoEnvio() == null) return;
        if (r.getCostoEnvio() < 0 || r.getCostoEnvio() > 500_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo de envío debe estar entre 0 y 500.000 COP");
        }
    }

    /** null = la tienda no hace envíos (solo retiro). */
    private static Double costoEnvioDe(RegistroNegocioRequest r) {
        if (Boolean.FALSE.equals(r.getOfreceEnvio()) || r.getCostoEnvio() == null) return null;
        return (double) Math.round(r.getCostoEnvio());
    }

    private TiendaDTO toDTO(Tienda t) {
        Suscripcion plan = suscripcionService.vigente(SuscripcionService.ACTOR_TIENDA, t.getId()).orElse(null);
        return TiendaDTO.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .ciudad(t.getCiudad())
                .barrio(t.getBarrio())
                .direccion(t.getDireccion())
                .latitud(t.getLatitud())
                .longitud(t.getLongitud())
                .nit(t.getNit())
                .telefono(t.getTelefono())
                .correoContacto(t.getCorreoContacto())
                .correoUsuario(cuentaNegocioService.correoDe(t.getUsuarioId()))
                .estadoAprobacion(t.getEstadoAprobacion())
                .motivoRechazo(t.getMotivoRechazo())
                .activo(t.getActivo())
                .horario(t.getHorario())
                .horarioSemanal(t.getHorarioSemanal())
                .costoEnvio(t.getCostoEnvio())
                .datosCompletos(t.tieneDatosCompletos())
                .fechaRegistro(t.getFechaRegistro())
                .fechaAprobacion(t.getFechaAprobacion())
                .planActual(plan != null ? plan.getPlanCodigo() : null)
                .planVence(plan != null ? plan.getFechaFin() : null)
                .logoUrl(t.getLogoUrl())
                .build();
    }
}
