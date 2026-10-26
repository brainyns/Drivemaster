package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.RecuperacionClave;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.RecuperacionClaveRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.LimiteIntentos;
import com.drivemaster.drivemaster.util.Correos;
import com.drivemaster.drivemaster.util.PlantillaCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * "¿Olvidaste tu contraseña?", con el mismo código de 6 dígitos del cambio de correo (CodigoVerificacion):
 * 1. solicitar: con el correo. Si es de una cuenta con contraseña (no Google) y activa, le llega el código.
 * 2. confirmar: con el correo, el código y la contraseña nueva. Cambia la contraseña y cierra todas las sesiones
 *    (Usuario.sesionesDesde: los tokens anteriores dejan de servir, también para renovar).
 *
 * No revela qué cuentas existen: solicitar responde siempre lo mismo y prepara el código en segundo plano (el tiempo
 * de respuesta tampoco cambia); si el correo no tiene cuenta, igual se guarda un pendiente con un código que nunca se
 * envía, así confirmar responde lo mismo ("Código incorrecto. Te quedan N intentos") exista o no.
 */
@Service
public class RecuperacionClaveService {

    public static final String MENSAJE_ENVIADO = "Si el correo está registrado, te enviamos un código de 6 dígitos. "
            + "Revisa tu bandeja de entrada y la carpeta de spam.";
    public static final String MENSAJE_LISTO = "Tu contraseña cambió y cerramos todas tus sesiones. Inicia sesión con la nueva.";
    public static final String MENSAJE_SIN_CODIGO = "El código no es válido o venció. Pide uno nuevo";
    // Las mismas reglas del registro (CuentaNegocioService y el formulario de registro de negocios)
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 128;

    private static final Logger log = LoggerFactory.getLogger(RecuperacionClaveService.class);

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final RecuperacionClaveRepository pendientes;
    private final CodigoVerificacion codigos;
    private final PasswordEncoder passwordEncoder;
    private final EmailsService emailsService;
    private final LimiteIntentos limiteIntentos;
    private final Executor segundoPlano;

    @Autowired
    public RecuperacionClaveService(UsuarioService usuarioService, UsuarioRepository usuarioRepository,
                                    RecuperacionClaveRepository pendientes, CodigoVerificacion codigos,
                                    PasswordEncoder passwordEncoder, EmailsService emailsService,
                                    LimiteIntentos limiteIntentos) {
        this(usuarioService, usuarioRepository, pendientes, codigos, passwordEncoder, emailsService, limiteIntentos,
                Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "recuperar-clave");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /** Con el Executor a mano: las pruebas lo corren en el mismo hilo. */
    RecuperacionClaveService(UsuarioService usuarioService, UsuarioRepository usuarioRepository,
                             RecuperacionClaveRepository pendientes, CodigoVerificacion codigos,
                             PasswordEncoder passwordEncoder, EmailsService emailsService,
                             LimiteIntentos limiteIntentos, Executor segundoPlano) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.pendientes = pendientes;
        this.codigos = codigos;
        this.passwordEncoder = passwordEncoder;
        this.emailsService = emailsService;
        this.limiteIntentos = limiteIntentos;
        this.segundoPlano = segundoPlano;
    }

    /** Siempre devuelve MENSAJE_ENVIADO (salvo un correo mal escrito o demasiados intentos). */
    public String solicitar(String correo, String ip) {
        limiteIntentos.consumir("recuperar-ip:" + ip);
        String normalizado = Correos.normalizar(correo);
        if (!Correos.valido(normalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un correo válido, por ejemplo nombre@dominio.com");
        }
        limiteIntentos.consumir("recuperar:" + normalizado);
        segundoPlano.execute(() -> preparar(normalizado));
        return MENSAJE_ENVIADO;
    }

    /** Guarda el pendiente y, si la cuenta puede recuperarse, envía el código. Nunca lanza: corre en segundo plano. */
    void preparar(String correo) {
        try {
            Optional<Usuario> cuenta = usuarioService.buscarPorCorreo(correo).filter(RecuperacionClaveService::recuperable);
            CodigoVerificacion.Generado codigo = codigos.generar();
            Instant ahora = Instant.now();
            pendientes.deleteByCorreo(correo); // un código nuevo anula el anterior
            pendientes.save(RecuperacionClave.builder()
                    .correo(correo)
                    .usuarioId(cuenta.map(Usuario::getId).orElse(null))
                    .codigoHash(codigo.hash())
                    .vence(ahora.plus(CodigoVerificacion.VIGENCIA))
                    .intentos(0)
                    .creado(ahora)
                    .build());
            if (cuenta.isPresent()) {
                emailsService.enviarEmail(cuenta.get().getCorreo(), "Tu código para recuperar la contraseña - DriveMaster",
                        correoCodigo(codigo.codigo()));
            } else {
                log.info("Recuperación de contraseña pedida para un correo sin cuenta recuperable; no se envía código");
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar la recuperación de contraseña: {}", e.getMessage());
        }
    }

    public String confirmar(String correo, String codigo, String nuevaPassword, String ip) {
        limiteIntentos.consumir("recuperar-ip:" + ip);
        // La contraseña se revisa antes que el código: un error de escritura no gasta un intento
        if (nuevaPassword == null || nuevaPassword.length() < PASSWORD_MIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe tener al menos " + PASSWORD_MIN + " caracteres");
        }
        if (nuevaPassword.length() > PASSWORD_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña no puede pasar de " + PASSWORD_MAX + " caracteres");
        }
        String normalizado = Correos.normalizar(correo);
        RecuperacionClave pendiente = (normalizado == null ? Optional.<RecuperacionClave>empty()
                : pendientes.findFirstByCorreoOrderByCreadoDesc(normalizado))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAJE_SIN_CODIGO));

        codigos.verificar(pendiente, codigo, () -> pendientes.save(pendiente), () -> pendientes.delete(pendiente));

        Usuario usuario = pendiente.getUsuarioId() == null ? null
                : usuarioRepository.findById(pendiente.getUsuarioId()).filter(RecuperacionClaveService::recuperable).orElse(null);
        pendientes.delete(pendiente);
        if (usuario == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAJE_SIN_CODIGO);

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        // El iat del JWT va en segundos: se trunca para que la sesión nueva (en este mismo segundo) sí valga
        usuario.setSesionesDesde(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        usuario.setIntentosFallidos(0);
        usuario.setBloqueado(false);
        usuario.setFechaBloqueo(null);
        usuarioRepository.save(usuario);

        try {
            emailsService.enviarEmail(usuario.getCorreo(), "Tu contraseña fue cambiada - DriveMaster", correoAviso());
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar del cambio de contraseña a la cuenta {}: {}", usuario.getId(), e.getMessage());
        }
        return MENSAJE_LISTO;
    }

    /** Solo cuentas activas que entran con contraseña: las de Google no tienen una que recuperar. */
    static boolean recuperable(Usuario u) {
        return u.getPassword() != null && !u.getPassword().isBlank()
                && !"GOOGLE".equalsIgnoreCase(u.getProveedor())
                && !Boolean.FALSE.equals(u.getActivo());
    }

    private static String correoCodigo(String codigo) {
        String cuerpo = "<p>Recibimos una solicitud para cambiar la contraseña de tu cuenta de DriveMaster. Escribe este"
                + " código en la pantalla donde lo pediste:</p>"
                + "<p style=\"font-size:32px;font-weight:700;letter-spacing:8px;margin:24px 0;\">"
                + HtmlUtils.htmlEscape(codigo) + "</p>"
                + "<p>Vence en " + CodigoVerificacion.VIGENCIA.toMinutes() + " minutos. Si no fuiste tú, ignora este"
                + " mensaje: tu contraseña no cambia sin este código.</p>";
        return PlantillaCorreo.documento("Recuperar contraseña", "Tu código de verificación", cuerpo,
                "Este es un mensaje automático de DriveMaster. Nunca te pediremos este código por teléfono.");
    }

    private static String correoAviso() {
        String cuerpo = "<p>La contraseña de tu cuenta fue cambiada y cerramos todas las sesiones abiertas.</p>"
                + "<p>Si no fuiste tú, contacta al administrador.</p>";
        return PlantillaCorreo.documento("Seguridad de la cuenta", "Tu contraseña fue cambiada", cuerpo,
                "Este es un mensaje automático de DriveMaster.");
    }
}
