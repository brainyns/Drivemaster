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
import com.drivemaster.drivemaster.dto.UsuarioAdminDTO;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.service.CuentaNegocioService;
import com.drivemaster.drivemaster.service.NotificacionService;
import com.drivemaster.drivemaster.service.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CuentaNegocioService cuentaNegocioService;
    private final NotificacionService notificacionService;

    public UsuarioController(UsuarioService usuarioService,
                             UsuarioRepository usuarioRepository,
                             PasswordEncoder passwordEncoder,
                             CuentaNegocioService cuentaNegocioService,
                             NotificacionService notificacionService) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.cuentaNegocioService = cuentaNegocioService;
        this.notificacionService = notificacionService;
    }

    // Las respuestas son UsuarioAdminDTO: nunca la entidad (googleId, permisos, intentos fallidos, bloqueo...)
    @GetMapping
    public List<UsuarioAdminDTO> listarUsuarios() {
        return usuarioService.listarTodos().stream().map(UsuarioAdminDTO::de).toList();
    }

    @PostMapping
    public ResponseEntity<UsuarioAdminDTO> crearUsuario(@RequestBody RegisterRequest request) {
        if (request.getCorreo() == null || request.getCorreo().isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");
        }

        if (usuarioRepository.findByCorreo(request.getCorreo()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Correo ya registrado");
        }

        // Desde aquí solo se crean cuentas del equipo de DriveMaster. Las tiendas y los talleres necesitan perfil y
        // aprobación: se registran con "Registra tu negocio" y se aprueban en Aprobaciones. VENDEDOR ya no existe.
        String rol = request.getRol() == null ? "" : request.getRol().trim().toUpperCase();
        if (!ROLES_CREABLES.contains(rol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Desde aquí solo se crean usuarios ADMIN o SUPERADMIN. Las tiendas y los talleres se registran con "
                            + "\"Registra tu negocio\" y se aprueban en Aprobaciones.");
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

        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioAdminDTO.de(usuarioService.crearUsuario(usuario)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioAdminDTO> actualizarUsuario(@PathVariable String id, @RequestBody RegisterRequest request, Authentication auth) {
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
            if (!ROLES_CREABLES.contains(rol)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol solo puede ser ADMIN o SUPERADMIN");
            }
            // Una cuenta de cliente, tienda o taller tiene su perfil: cambiarle el rol la dejaría rota
            if (!ROLES_CREABLES.contains(usuario.getRol()) && !"VENDEDOR".equals(usuario.getRol())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El rol de un cliente, una tienda o un taller no se cambia: tiene perfil propio");
            }
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

        Usuario actualizado = usuarioService.actualizarUsuario(usuario);
        // Una cuenta de taller o tienda desactivada pausa su negocio (reactivarlo es manual, en Aprobaciones)
        if (Boolean.FALSE.equals(request.getActivo())) {
            cuentaNegocioService.pausarNegocioDe(usuario);
            notificacionService.cerrarConexionesDe(usuario.getId());
        }
        return ResponseEntity.ok(UsuarioAdminDTO.de(actualizado));
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
        // Una cuenta de taller o tienda desactivada pausa su negocio (reactivarlo es manual, en Aprobaciones)
        cuentaNegocioService.pausarNegocioDe(usuario);
        notificacionService.cerrarConexionesDe(usuario.getId());
        return ResponseEntity.ok().build();
    }

    /** Roles que se pueden crear o asignar desde la gestión de usuarios. */
    static final java.util.Set<String> ROLES_CREABLES = java.util.Set.of("ADMIN", "SUPERADMIN");

    private List<String> getPermisosPorRol(String rol) {
        return RolPermisos.de(rol);
    }
}
