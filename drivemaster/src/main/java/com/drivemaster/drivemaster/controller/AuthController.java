package com.drivemaster.drivemaster.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getPassword()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getCorreo());
        String token = jwtUtil.generateToken(userDetails);
        Usuario usuario = usuarioService.obtenerPorCorreo(request.getCorreo());

        return ResponseEntity.ok(new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol()));
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

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setCorreo(request.getCorreo());
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario = usuarioService.crearUsuario(usuario);

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getCorreo());
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol()));
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
}
