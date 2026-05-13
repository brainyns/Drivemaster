package com.drivemaster.drivemaster.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.AuthRequest;
import com.drivemaster.drivemaster.dto.AuthResponse;
import com.drivemaster.drivemaster.dto.RegisterRequest;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.CustomUserDetailsService;
import com.drivemaster.drivemaster.security.JwtUtil;
import com.drivemaster.drivemaster.service.UsuarioService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

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
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
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
        String accessToken = jwtUtil.generateToken(userDetails);

        usuario.setUltimoLogin(Instant.now());
        usuarioRepository.save(usuario);

        Cookie jwtCookie = new Cookie("jwt", accessToken);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge((int) (expirationMs / 1000));
        response.addCookie(jwtCookie);

        return ResponseEntity.ok(new AuthResponse(accessToken, usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol()));
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

        String rol = determineRole(request.getRol());
        if (usuarioRepository.count() == 0) {
            rol = "SUPERADMIN";
        }

        List<String> permisos = getPermisosPorRol(rol);

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .correo(request.getCorreo())
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(rol)
                .permisos(permisos)
                .activo(true)
                .fechaCreacion(Instant.now())
                .intentosFallidos(0)
                .bloqueado(false)
                .build();
        usuario = usuarioService.crearUsuario(usuario);

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getCorreo());
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido");
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        if (jwtUtil.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expirado");
        }

        Usuario usuario = usuarioService.obtenerPorCorreo(username);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String newToken = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(newToken, usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol()));
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
        return switch (rol) {
            case "SUPERADMIN" -> List.of(
                    "USUARIOS_READ", "USUARIOS_WRITE", "USUARIOS_DELETE",
                    "PRODUCTOS_READ", "PRODUCTOS_WRITE", "PRODUCTOS_DELETE",
                    "CLIENTES_READ", "CLIENTES_WRITE", "CLIENTES_DELETE",
                    "VENTAS_READ", "VENTAS_WRITE", "VENTAS_DELETE",
                    "COMPRAS_READ", "COMPRAS_WRITE", "COMPRAS_DELETE",
                    "PROVEEDORES_READ", "PROVEEDORES_WRITE", "PROVEEDORES_DELETE",
                    "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE",
                    "AJUSTES_READ", "AJUSTES_WRITE"
            );
            case "ADMIN" -> List.of(
                    "USUARIOS_READ", "USUARIOS_WRITE",
                    "PRODUCTOS_READ", "PRODUCTOS_WRITE",
                    "CLIENTES_READ", "CLIENTES_WRITE",
                    "VENTAS_READ", "VENTAS_WRITE",
                    "COMPRAS_READ", "COMPRAS_WRITE",
                    "PROVEEDORES_READ", "PROVEEDORES_WRITE",
                    "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE",
                    "AJUSTES_READ", "AJUSTES_WRITE"
            );
            case "VENDEDOR" -> List.of(
                    "PRODUCTOS_READ",
                    "CLIENTES_READ", "CLIENTES_WRITE",
                    "VENTAS_READ", "VENTAS_WRITE"
            );
            default -> List.of();
        };
    }
}
