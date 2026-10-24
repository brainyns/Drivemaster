package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CambioCorreo;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.CambioCorreoRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.LimiteIntentos;
import com.drivemaster.drivemaster.util.Correos;
import com.drivemaster.drivemaster.util.PlantillaCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Cambio del correo de la cuenta (el que se usa para iniciar sesión), en dos pasos:
 * 1. solicitar: con la contraseña actual y el correo nuevo. Si nadie más lo usa, envía un código de 6 dígitos a ese
 *    correo (vence en 15 minutos).
 * 2. confirmar: con el código (máximo 5 intentos). Cambia el correo, avisa al correo anterior y la sesión se vuelve a
 *    emitir con el correo nuevo (el token lleva el correo, así que las sesiones abiertas en otros equipos se cierran).
 */
@Service
public class CambioCorreoService {

    public static final Duration VIGENCIA = Duration.ofMinutes(15);
    public static final int MAX_INTENTOS = 5;

    private static final Logger log = LoggerFactory.getLogger(CambioCorreoService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final CambioCorreoRepository cambios;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailsService emailsService;
    private final LimiteIntentos limiteIntentos;

    public CambioCorreoService(UsuarioService usuarioService, UsuarioRepository usuarioRepository,
                               CambioCorreoRepository cambios, TiendaRepository tiendaRepository,
                               PasswordEncoder passwordEncoder, EmailsService emailsService,
                               LimiteIntentos limiteIntentos) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.cambios = cambios;
        this.tiendaRepository = tiendaRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailsService = emailsService;
        this.limiteIntentos = limiteIntentos;
    }

    /** Lo que ve quien pidió el código: a qué correo se envió y cuándo vence. */
    public record Pendiente(String correoNuevo, Instant vence, int intentosRestantes) {
    }

    public Pendiente solicitar(String correoSesion, String passwordActual, String correoNuevo) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoSesion);
        // Frena el envío masivo de códigos y los intentos de adivinar la contraseña desde una sesión abierta
        limiteIntentos.consumir("cambio-correo:" + usuario.getId());

        if (passwordActual == null || passwordActual.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe tu contraseña actual");
        }
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tu cuenta entra con Google y no tiene contraseña: el correo lo cambia el administrador de DriveMaster");
        }
        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        String nuevo = Correos.normalizar(correoNuevo);
        if (!Correos.valido(nuevo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un correo válido, por ejemplo nombre@dominio.com");
        }
        if (nuevo.equals(Correos.normalizar(usuario.getCorreo()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese ya es el correo de tu cuenta");
        }
        if (usuarioService.correoOcupado(nuevo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está registrado en DriveMaster");
        }

        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant ahora = Instant.now();
        cambios.deleteByUsuarioId(usuario.getId()); // un código nuevo anula el anterior
        CambioCorreo cambio = cambios.save(CambioCorreo.builder()
                .usuarioId(usuario.getId())
                .correoNuevo(nuevo)
                .codigoHash(passwordEncoder.encode(codigo))
                .vence(ahora.plus(VIGENCIA))
                .intentos(0)
                .creado(ahora)
                .build());

        try {
            emailsService.enviarEmail(nuevo, "Tu código para cambiar el correo - DriveMaster", correoCodigo(codigo));
        } catch (RuntimeException e) {
            cambios.delete(cambio);
            log.warn("No se pudo enviar el código de cambio de correo de la cuenta {}: {}", usuario.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No pudimos enviar el código a ese correo. Inténtalo de nuevo en unos minutos");
        }
        return new Pendiente(nuevo, cambio.getVence(), MAX_INTENTOS);
    }

    /** Valida el código y cambia el correo. Devuelve el usuario ya con el correo nuevo. */
    public Usuario confirmar(String correoSesion, String codigo) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoSesion);
        CambioCorreo cambio = cambios.findFirstByUsuarioIdOrderByCreadoDesc(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No tienes un cambio de correo pendiente. Empieza de nuevo"));

        if (Instant.now().isAfter(cambio.getVence())) {
            cambios.delete(cambio);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código venció. Pide uno nuevo");
        }
        if (cambio.getIntentos() >= MAX_INTENTOS) {
            cambios.delete(cambio);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agotaste los " + MAX_INTENTOS + " intentos. Pide un código nuevo");
        }
        String limpio = codigo == null ? "" : codigo.replaceAll("\\s+", "");
        if (!limpio.matches("\\d{6}") || !passwordEncoder.matches(limpio, cambio.getCodigoHash())) {
            cambio.setIntentos(cambio.getIntentos() + 1);
            int quedan = MAX_INTENTOS - cambio.getIntentos();
            if (quedan <= 0) {
                cambios.delete(cambio);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Código incorrecto. Agotaste los " + MAX_INTENTOS + " intentos: pide un código nuevo");
            }
            cambios.save(cambio);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Código incorrecto. Te " + (quedan == 1 ? "queda 1 intento" : "quedan " + quedan + " intentos"));
        }

        String nuevo = cambio.getCorreoNuevo();
        // Alguien pudo registrarse con ese correo mientras llegaba el código
        if (usuarioService.correoOcupado(nuevo)) {
            cambios.delete(cambio);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está registrado en DriveMaster");
        }
        String anterior = usuario.getCorreo();
        usuario.setCorreo(nuevo);
        try {
            usuario = usuarioRepository.save(usuario);
        } catch (DuplicateKeyException e) {
            cambios.delete(cambio);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está registrado en DriveMaster");
        }
        cambios.delete(cambio);

        // La tienda guarda su correo de contacto aparte: si era el de la cuenta, sigue siéndolo
        String usuarioId = usuario.getId();
        tiendaRepository.findByUsuarioId(usuarioId)
                .filter(t -> t.getCorreoContacto() == null || t.getCorreoContacto().equalsIgnoreCase(anterior))
                .ifPresent(t -> {
                    t.setCorreoContacto(nuevo);
                    tiendaRepository.save(t);
                });

        try {
            emailsService.enviarEmail(anterior, "El correo de tu cuenta fue cambiado - DriveMaster", correoAviso());
        } catch (RuntimeException e) {
            // El cambio ya está hecho; solo queda registro de que el aviso no salió
            log.warn("No se pudo avisar del cambio de correo a la dirección anterior de la cuenta {}: {}", usuarioId, e.getMessage());
        }
        return usuario;
    }

    /** Descarta el cambio pendiente (el código deja de servir). */
    public void cancelar(String correoSesion) {
        cambios.deleteByUsuarioId(usuarioService.obtenerPorCorreo(correoSesion).getId());
    }

    private static String correoCodigo(String codigo) {
        String cuerpo = "<p>Recibimos una solicitud para usar este correo en tu cuenta de DriveMaster. Escribe este código"
                + " en la pantalla donde lo pediste:</p>"
                + "<p style=\"font-size:32px;font-weight:700;letter-spacing:8px;margin:24px 0;\">"
                + HtmlUtils.htmlEscape(codigo) + "</p>"
                + "<p>Vence en " + VIGENCIA.toMinutes() + " minutos. Si no fuiste tú, ignora este mensaje: el correo de"
                + " la cuenta no cambia sin este código.</p>";
        return PlantillaCorreo.documento("Cambio de correo", "Tu código de verificación", cuerpo,
                "Este es un mensaje automático de DriveMaster. Nunca te pediremos este código por teléfono.");
    }

    private static String correoAviso() {
        String cuerpo = "<p>El correo de tu cuenta fue cambiado. Si no fuiste tú, contacta al administrador.</p>"
                + "<p>Desde ahora, para iniciar sesión se usa el correo nuevo. Este correo ya no recibirá los avisos de"
                + " la cuenta.</p>";
        return PlantillaCorreo.documento("Seguridad de la cuenta", "El correo de tu cuenta fue cambiado", cuerpo,
                "Este es un mensaje automático de DriveMaster.");
    }
}
