package com.drivemaster.drivemaster.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.RegisterRequest;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioService usuarioService,
                             UsuarioRepository usuarioRepository,
                             PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Usuario> listarUsuarios() {
        return usuarioService.listarTodos();
    }

    @PostMapping
    public ResponseEntity<Usuario> crearUsuario(@RequestBody RegisterRequest request) {
        if (request.getCorreo() == null || request.getCorreo().isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");
        }

        if (usuarioRepository.findByCorreo(request.getCorreo()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Correo ya registrado");
        }

        String rol = request.getRol() == null ? "VENDEDOR" : request.getRol().trim().toUpperCase();
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

        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crearUsuario(usuario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> actualizarUsuario(@PathVariable String id, @RequestBody RegisterRequest request, Authentication auth) {
        Usuario usuario = usuarioService.obtenerPorId(id);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        if (request.getNombre() != null) {
            usuario.setNombre(request.getNombre());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRol() != null) {
            String authRol = usuarioRepository.findByCorreo(auth.getName())
                    .map(Usuario::getRol).orElse("");
            if (!"SUPERADMIN".equals(authRol)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo SUPERADMIN puede cambiar roles");
            }
            String rol = request.getRol().trim().toUpperCase();
            usuario.setRol(rol);
            usuario.setPermisos(getPermisosPorRol(rol));
        }

        if (request.getActivo() != null) {
            String authRol = usuarioRepository.findByCorreo(auth.getName())
                    .map(Usuario::getRol).orElse("");
            if (!"SUPERADMIN".equals(authRol)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo SUPERADMIN puede activar/desactivar usuarios");
            }
            usuario.setActivo(request.getActivo());
        }

        return ResponseEntity.ok(usuarioService.actualizarUsuario(usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable String id, Authentication auth) {
        String authRol = usuarioRepository.findByCorreo(auth.getName())
                .map(Usuario::getRol).orElse("");
        if (!"SUPERADMIN".equals(authRol)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo SUPERADMIN puede eliminar usuarios");
        }

        Usuario usuario = usuarioService.obtenerPorId(id);
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        usuario.setActivo(false);
        usuarioService.actualizarUsuario(usuario);
        return ResponseEntity.ok().build();
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
