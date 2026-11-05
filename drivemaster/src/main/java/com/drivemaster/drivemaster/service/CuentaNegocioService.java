package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.util.Correos;
import com.drivemaster.drivemaster.util.PatronBusqueda;
import com.drivemaster.drivemaster.util.PlantillaCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Lógica común a las cuentas de negocio (talleres y tiendas): creación del
 * usuario de acceso, validación del registro y aviso del resultado de la revisión.
 */
@Service
public class CuentaNegocioService {

    private static final Logger log = LoggerFactory.getLogger(CuentaNegocioService.class);
    private static final int PASSWORD_MIN = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailsService emailsService;
    private final TallerRepository tallerRepository;
    private final TiendaRepository tiendaRepository;

    public CuentaNegocioService(UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder,
                                EmailsService emailsService,
                                TallerRepository tallerRepository,
                                TiendaRepository tiendaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailsService = emailsService;
        this.tallerRepository = tallerRepository;
        this.tiendaRepository = tiendaRepository;
    }

    /**
     * Guarda el negocio de una cuenta recién creada. Si falla, borra esa cuenta y relanza el error:
     * sin transacción entre las dos colecciones, así no queda un usuario sin negocio ni el correo ocupado.
     */
    public <T> T conCuentaNueva(Usuario cuentaNueva, Supplier<T> guardarNegocio) {
        try {
            return guardarNegocio.get();
        } catch (RuntimeException e) {
            try {
                usuarioRepository.deleteById(cuentaNueva.getId());
                log.warn("Registro de negocio fallido: se borró la cuenta recién creada {}", cuentaNueva.getCorreo());
            } catch (RuntimeException borrado) {
                log.error("No se pudo borrar la cuenta {} tras fallar el registro de su negocio",
                        cuentaNueva.getCorreo(), borrado);
                e.addSuppressed(borrado);
            }
            throw e;
        }
    }

    /**
     * Pausa (activo = false) el taller o la tienda de una cuenta que se desactiva: sin dueño que pueda entrar,
     * el negocio no debe seguir recibiendo solicitudes ni pedidos. Reactivarlo es manual, desde Aprobaciones.
     */
    public void pausarNegocioDe(Usuario cuenta) {
        if (cuenta == null || cuenta.getId() == null) return;
        if (RolPermisos.TALLER.equals(cuenta.getRol())) {
            tallerRepository.findByUsuarioId(cuenta.getId())
                    .filter(t -> !Boolean.FALSE.equals(t.getActivo()))
                    .ifPresent(t -> {
                        t.setActivo(false);
                        tallerRepository.save(t);
                        log.info("Taller {} pausado: se desactivó su cuenta {}", t.getNombre(), cuenta.getCorreo());
                    });
        } else if (RolPermisos.TIENDA.equals(cuenta.getRol())) {
            tiendaRepository.findByUsuarioId(cuenta.getId())
                    .filter(t -> !Boolean.FALSE.equals(t.getActivo()))
                    .ifPresent(t -> {
                        t.setActivo(false);
                        tiendaRepository.save(t);
                        log.info("Tienda {} pausada: se desactivó su cuenta {}", t.getNombre(), cuenta.getCorreo());
                    });
        }
    }

    public void validarRegistro(RegistroNegocioRequest r) {
        if (r == null || vacio(r.getNombreNegocio()) || vacio(r.getCiudad()) || vacio(r.getDireccion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nombre del negocio, ciudad y dirección son obligatorios");
        }
        if (vacio(r.getCorreo()) || vacio(r.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");
        }
    }

    public static final String TIENDA = "TIENDA";
    public static final String TALLER = "TALLER";

    /**
     * Antes de crear una tienda o un taller: que no haya otro del mismo tipo con el mismo nombre o NIT, ni una tienda
     * con el mismo correo de contacto. Se compara sin mayúsculas, tildes ni espacios ("Taller  Central" = "taller
     * central"), y el NIT solo por sus dígitos ("900.123.456-7" = "9001234567"). Bloquean los aprobados, los
     * pendientes de aprobación y los archivados; los rechazados no, para que puedan volver a registrarse con sus datos
     * corregidos. (El correo de la cuenta ya es único para todos: lo revisa crearUsuario.)
     */
    public void validarSinDuplicados(String tipo, RegistroNegocioRequest r) {
        String nombre = clave(r.getNombreNegocio());
        String nit = soloDigitos(r.getNit());
        String correo = clave(r.getCorreo());
        String etiqueta = TALLER.equals(tipo) ? "un taller registrado" : "una tienda registrada";
        if (TALLER.equals(tipo)) {
            for (Taller t : tallerRepository.findAll()) {
                if (!bloquea(t.getEstadoAprobacion())) continue;
                if (nombre != null && nombre.equals(clave(t.getNombre()))) throw duplicado(etiqueta, "ese nombre");
                if (nit != null && nit.equals(soloDigitos(t.getNit()))) throw duplicado(etiqueta, "ese NIT");
            }
        } else {
            for (Tienda t : tiendaRepository.findAll()) {
                if (!bloquea(t.getEstadoAprobacion())) continue;
                if (nombre != null && nombre.equals(clave(t.getNombre()))) throw duplicado(etiqueta, "ese nombre");
                if (nit != null && nit.equals(soloDigitos(t.getNit()))) throw duplicado(etiqueta, "ese NIT");
                if (correo != null && correo.equals(clave(t.getCorreoContacto()))) throw duplicado(etiqueta, "ese correo");
            }
        }
    }

    /** Los rechazados no bloquean un registro nuevo; los demás (aprobados, pendientes, archivados o sin estado) sí. */
    static boolean bloquea(String estadoAprobacion) {
        return !EstadoAprobacion.RECHAZADO.equals(estadoAprobacion);
    }

    /** Minúsculas, sin tildes y sin espacios; null si queda vacío. */
    static String clave(String texto) {
        if (texto == null) return null;
        String c = PatronBusqueda.normalizar(texto).replaceAll("\\s+", "");
        return c.isEmpty() ? null : c;
    }

    /** Solo los dígitos del NIT; null si no tiene ninguno. */
    static String soloDigitos(String nit) {
        if (nit == null) return null;
        String d = nit.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static ResponseStatusException duplicado(String etiqueta, String dato) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay " + etiqueta + " con " + dato + ".");
    }

    /** Crea el usuario que inicia sesión en nombre del negocio. */
    public Usuario crearUsuario(String nombre, String correo, String password,
                                String telefono, String ciudad, String rol) {
        if (vacio(correo) || vacio(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");
        }
        if (password.length() < PASSWORD_MIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos " + PASSWORD_MIN + " caracteres");
        }
        String correoNormalizado = Correos.normalizar(correo);
        if (!Correos.valido(correoNormalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un correo válido, por ejemplo nombre@dominio.com");
        }
        // Exacto (usa el índice) y sin mayúsculas, por las cuentas antiguas guardadas con mayúsculas
        if (usuarioRepository.existsByCorreo(correoNormalizado)
                || !usuarioRepository.findByCorreoIgnoreCase(correoNormalizado).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
        }

        Usuario usuario = Usuario.builder()
                .nombre(nombre)
                .correo(correoNormalizado)
                .password(passwordEncoder.encode(password))
                .telefono(telefono)
                .ciudad(ciudad)
                .proveedor("LOCAL")
                .rol(rol)
                .permisos(RolPermisos.de(rol))
                .activo(true)
                .datosCompletos(true)
                .fechaCreacion(Instant.now())
                .intentosFallidos(0)
                .bloqueado(false)
                .build();
        return usuarioRepository.save(usuario);
    }

    /** Correo al negocio con el resultado de la revisión. Un fallo de envío no revierte la decisión. */
    public void notificarResultado(String usuarioId, String nombreNegocio, boolean aprobado, String motivo) {
        if (usuarioId == null) return;
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            try {
                String nombre = HtmlUtils.htmlEscape(nombreNegocio != null ? nombreNegocio : "tu negocio");
                String cuerpo = aprobado
                        ? "<p>¡Buenas noticias! <strong>" + nombre + "</strong> fue aprobado en DriveMaster."
                          + " Ya puedes iniciar sesión y empezar a operar.</p>"
                        : "<p>Revisamos la solicitud de <strong>" + nombre + "</strong> y por ahora no fue aprobada.</p>"
                          + "<p><strong>Motivo:</strong> " + HtmlUtils.htmlEscape(motivo != null ? motivo : "—") + "</p>";
                String html = PlantillaCorreo.documento("Registro de negocio",
                        aprobado ? "Tu cuenta fue aprobada" : "Resultado de tu registro", cuerpo,
                        "Este es un mensaje automático de DriveMaster.");
                emailsService.enviarEmail(usuario.getCorreo(),
                        aprobado ? "Tu cuenta en DriveMaster fue aprobada" : "Resultado de tu registro en DriveMaster",
                        html);
            } catch (Exception e) {
                log.warn("No se pudo notificar la revisión de {}: {}", nombreNegocio, e.getMessage());
            }
        });
    }

    public void validarMotivoRechazo(Boolean aprobado, String motivo) {
        if (aprobado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique si se aprueba o se rechaza");
        }
        if (!aprobado && vacio(motivo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo del rechazo");
        }
    }

    public String correoDe(String usuarioId) {
        if (usuarioId == null) return null;
        return usuarioRepository.findById(usuarioId).map(Usuario::getCorreo).orElse(null);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
