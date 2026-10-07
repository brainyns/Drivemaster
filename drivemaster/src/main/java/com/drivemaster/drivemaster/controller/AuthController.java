package com.drivemaster.drivemaster.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.drivemaster.drivemaster.service.GoogleAuthService;
import com.drivemaster.drivemaster.dto.AuthRequest;
import com.drivemaster.drivemaster.dto.AuthResponse;
import com.drivemaster.drivemaster.dto.GoogleLoginRequest;
import com.drivemaster.drivemaster.dto.RefreshRequest;
import com.drivemaster.drivemaster.dto.RegisterRequest;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.CustomUserDetailsService;
import com.drivemaster.drivemaster.security.JwtUtil;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.service.UsuarioService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final GoogleAuthService googleAuthService;

    @Value("${security.jwt.refresh-expiration-ms:86400000}")
    private long refreshTokenExpirationMs;

    @Value("${security.jwt.expiration-ms:1800000}")
    private long expirationMs;

    @Value("${security.session.max-intentos:5}")
    private int maxIntentosFallidos;

    @Value("${security.session.bloqueo-minutos:15}")
    private int tiempoBloqueoMinutos;

    public AuthController(AuthenticationManager authenticationManager,
                          UsuarioService usuarioService,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          CustomUserDetailsService userDetailsService,
                          JwtUtil jwtUtil,
                          GoogleAuthService googleAuthService) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(request.getCorreo());

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            if (usuario.getBloqueado() != null && usuario.getBloqueado()) {
                if (usuario.getFechaBloqueo() != null) {
                    Instant tiempoDesbloqueo = usuario.getFechaBloqueo().plusSeconds(tiempoBloqueoMinutos * 60L);
                    if (Instant.now().isBefore(tiempoDesbloqueo)) {
                        throw new LockedException("Cuenta bloqueada. Intente más tarde.");
                    } else {
                        usuario.setBloqueado(false);
                        usuario.setIntentosFallidos(0);
                        usuario.setFechaBloqueo(null);
                        usuarioRepository.save(usuario);
                    }
                }
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getPassword()));
        } catch (Exception e) {
            handleFailedLogin(usuarioOpt.orElse(null));
            throw new BadCredentialsException("Credenciales inválidas");
        }

        Usuario usuario = usuarioService.obtenerPorCorreo(request.getCorreo());
        resetFailedLogin(usuario);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getCorreo());
        AuthResponse sesion = construirSesion(usuario, userDetails);

        usuario.setUltimoLogin(Instant.now());
        usuarioRepository.save(usuario);

        agregarCookieJwt(response, sesion.getToken());

        return ResponseEntity.ok(sesion);
    }

    @PostMapping("/login-interno")
    public ResponseEntity<?> loginInterno(@RequestBody AuthRequest request, HttpServletResponse response) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCorreo(request.getCorreo());

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            if ("CLIENTE".equals(usuario.getRol())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Acceso solo para personal interno"));
            }

            if (usuario.getBloqueado() != null && usuario.getBloqueado()) {
                if (usuario.getFechaBloqueo() != null) {
                    Instant tiempoDesbloqueo = usuario.getFechaBloqueo().plusSeconds(tiempoBloqueoMinutos * 60L);
                    if (Instant.now().isBefore(tiempoDesbloqueo)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "Cuenta bloqueada. Intente más tarde."));
                    } else {
                        usuario.setBloqueado(false);
                        usuario.setIntentosFallidos(0);
                        usuario.setFechaBloqueo(null);
                        usuarioRepository.save(usuario);
                    }
                }
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getPassword()));
        } catch (Exception e) {
            handleFailedLogin(usuarioOpt.orElse(null));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas"));
        }

        Usuario usuario = usuarioService.obtenerPorCorreo(request.getCorreo());
        resetFailedLogin(usuario);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getCorreo());
        AuthResponse sesion = construirSesion(usuario, userDetails);

        usuario.setUltimoLogin(Instant.now());
        usuarioRepository.save(usuario);

        agregarCookieJwt(response, sesion.getToken());

        return ResponseEntity.ok(sesion);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
        Usuario usuario = googleAuthService.autenticarConGoogle(request.getIdToken());

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getCorreo());

        usuario.setUltimoLogin(Instant.now());
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(construirSesion(usuario, userDetails));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (request.getCorreo() == null || request.getCorreo().isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");
        }

        Optional<Usuario> existing = usuarioRepository.findByCorreo(request.getCorreo());
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Correo ya registrado");
        }

        String rol = "CLIENTE";
        List<String> permisos = getPermisosPorRol(rol);

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .correo(request.getCorreo())
                .password(passwordEncoder.encode(request.getPassword()))
                .identificacion(request.getIdentificacion())
                .proveedor("LOCAL")
                .rol(rol)
                .permisos(permisos)
                .activo(true)
                .datosCompletos(false)
                .fechaCreacion(Instant.now())
                .intentosFallidos(0)
                .bloqueado(false)
                .build();
        usuario = usuarioService.crearUsuario(usuario);

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getCorreo());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(construirSesion(usuario, userDetails));
    }

    /**
     * Renueva la sesión. Acepta el refresh token en el body (flujo normal) o, por
     * compatibilidad con sesiones abiertas antes de este cambio, un token de acceso
     * todavía vigente en la cabecera Authorization.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody(required = false) RefreshRequest body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token;
        boolean esRefresh;
        if (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank()) {
            token = body.getRefreshToken();
            esRefresh = true;
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            esRefresh = false;
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido");
        }

        String username;
        try {
            // parseClaimsJws valida firma y vencimiento; lanza excepción si no sirve.
            username = jwtUtil.extractUsername(token);
            if (jwtUtil.isRefreshToken(token) != esRefresh) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tipo de token inválido");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada, inicie sesión nuevamente");
        }

        Usuario usuario = usuarioService.obtenerPorCorreo(username);
        if (Boolean.FALSE.equals(usuario.getActivo()) || Boolean.TRUE.equals(usuario.getBloqueado())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta no está activa");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return ResponseEntity.ok(construirSesion(usuario, userDetails));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String passwordActual = body.get("passwordActual");
        String nuevaPassword = body.get("nuevaPassword");

        if (passwordActual == null || passwordActual.isBlank() ||
            nuevaPassword == null || nuevaPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ambas contraseñas son obligatorias"));
        }

        if (nuevaPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La nueva contraseña debe tener al menos 6 caracteres"));
        }

        Usuario usuario = usuarioService.obtenerPorCorreo(auth.getName());

        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "La contraseña actual no es correcta"));
        }

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie jwtCookie = new Cookie("jwt", "");
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0);
        response.addCookie(jwtCookie);
        return ResponseEntity.ok().build();
    }

    private AuthResponse construirSesion(Usuario usuario, UserDetails userDetails) {
        return new AuthResponse(
                jwtUtil.generateToken(userDetails), usuario.getId(), usuario.getNombre(),
                usuario.getCorreo(), usuario.getRol(),
                usuario.getProveedor(), usuario.getDatosCompletos(),
                jwtUtil.generateRefreshToken(userDetails));
    }

    private void agregarCookieJwt(HttpServletResponse response, String accessToken) {
        Cookie jwtCookie = new Cookie("jwt", accessToken);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge((int) (expirationMs / 1000));
        response.addCookie(jwtCookie);
    }

    private void handleFailedLogin(Usuario usuario) {
        if (usuario == null) return;

        Integer intentos = usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos();
        boolean bloqueado = usuario.getBloqueado() != null && usuario.getBloqueado();

        if (!bloqueado) {
            intentos++;
            usuario.setIntentosFallidos(intentos);

            if (intentos >= maxIntentosFallidos) {
                usuario.setBloqueado(true);
                usuario.setFechaBloqueo(Instant.now());
            }
            usuarioRepository.save(usuario);
        }
    }

    private void resetFailedLogin(Usuario usuario) {
        usuario.setIntentosFallidos(0);
        usuario.setBloqueado(false);
        usuario.setFechaBloqueo(null);
        usuarioRepository.save(usuario);
    }

    private String determineRole(String requestedRole) {
        if (requestedRole == null) {
            return "VENDEDOR";
        }
        String normalized = requestedRole.trim().toUpperCase();
        if (List.of("SUPERADMIN", "ADMIN", "VENDEDOR").contains(normalized)) {
            return normalized;
        }
        return "VENDEDOR";
    }

    private List<String> getPermisosPorRol(String rol) {
        return RolPermisos.de(rol);
    }
}