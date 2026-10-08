package com.drivemaster.drivemaster.controller;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.UpdateClienteRequest;
import com.drivemaster.drivemaster.dto.cliente.ClienteDTO;
import com.drivemaster.drivemaster.dto.cliente.DatosClienteRequest;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.PdfClienteBuilder;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ClienteController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final int PASSWORD_MIN = 6;

    private static boolean esSuperadmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + RolPermisos.SUPERADMIN).equals(a.getAuthority()));
    }

    private Usuario porId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private static boolean esCliente(Usuario u) {
        return RolPermisos.CLIENTE.equals(u.getRol());
    }

    /** Alta de un cliente por el SUPERADMIN. El rol y los permisos los pone el servidor; el cuerpo no los controla. */
    @PostMapping("/guardar")
    public ResponseEntity<ClienteDTO.Detalle> guardar(@RequestBody DatosClienteRequest datos) {
        if (datos.getCorreo() == null || datos.getCorreo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo es obligatorio");
        }
        String correo = datos.getCorreo().trim();
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }
        if (datos.getPassword() != null && !datos.getPassword().isBlank() && datos.getPassword().length() < PASSWORD_MIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos " + PASSWORD_MIN + " caracteres");
        }
        Usuario usuario = Usuario.builder()
                .nombre(datos.getNombre()).correo(correo)
                .identificacion(datos.getIdentificacion()).telefono(datos.getTelefono())
                .direccion(datos.getDireccion()).ciudad(datos.getCiudad())
                .region(datos.getRegion()).referencia(datos.getReferencia())
                .rol(RolPermisos.CLIENTE).permisos(RolPermisos.de(RolPermisos.CLIENTE))
                .proveedor("LOCAL").activo(true).datosCompletos(true)
                .fechaCreacion(Instant.now()).intentosFallidos(0).bloqueado(false)
                .build();
        if (datos.getPassword() != null && !datos.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(datos.getPassword()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteDTO.Detalle.de(usuarioRepository.save(usuario)));
    }

    /** Lista del SUPERADMIN: solo clientes activos y sin cédula, googleId ni permisos. */
    @GetMapping
    public List<ClienteDTO.Resumen> listarClientes() {
        return usuarioRepository.findAll().stream()
                .filter(ClienteController::esCliente)
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .map(ClienteDTO.Resumen::de)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO.Detalle> obtenerPorId(@PathVariable String id) {
        Usuario u = porId(id);
        if (!esCliente(u)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        return ResponseEntity.ok(ClienteDTO.Detalle.de(u));
    }

    /**
     * Cada usuario edita solo su cuenta (la ruta debe coincidir con la de la sesión); el SUPERADMIN edita cualquier
     * cliente. Solo se aplican los datos personales: rol, permisos, estado y correo no se tocan desde aquí.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClienteDTO.Detalle> actualizar(@PathVariable String id, @RequestBody DatosClienteRequest datos,
                                                         Authentication auth) {
        Usuario existente = porId(id);
        boolean propia = auth.getName().equals(existente.getCorreo());
        if (!propia) {
            if (!esSuperadmin(auth)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes editar tu propia cuenta");
            }
            if (!esCliente(existente)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
            }
        }
        if (datos.getNombre() != null && !datos.getNombre().isBlank()) existente.setNombre(datos.getNombre());
        if (datos.getIdentificacion() != null) existente.setIdentificacion(datos.getIdentificacion());
        if (datos.getTelefono() != null) existente.setTelefono(datos.getTelefono());
        if (datos.getDireccion() != null) existente.setDireccion(datos.getDireccion());
        if (datos.getCiudad() != null) existente.setCiudad(datos.getCiudad());
        if (datos.getRegion() != null) existente.setRegion(datos.getRegion());
        if (datos.getReferencia() != null) existente.setReferencia(datos.getReferencia());
        return ResponseEntity.ok(ClienteDTO.Detalle.de(usuarioRepository.save(existente)));
    }

    /**
     * "Eliminar" desactiva la cuenta (no puede iniciar sesión y conserva su historial). Nunca a un SUPERADMIN ni a
     * uno mismo.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id, Authentication auth) {
        Usuario objetivo = porId(id);
        if (auth.getName().equals(objetivo.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes desactivar tu propia cuenta");
        }
        if (RolPermisos.SUPERADMIN.equals(objetivo.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No se puede desactivar a un SUPERADMIN");
        }
        if (!esCliente(objetivo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta cuenta no es de un cliente");
        }
        objetivo.setActivo(false);
        usuarioRepository.save(objetivo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mi-perfil")
    public ResponseEntity<?> miPerfil(Authentication auth) {
        return usuarioRepository.findByCorreo(auth.getName())
                .map(usuario -> ResponseEntity.ok().body((Object) usuario))
                .orElse(ResponseEntity.ok().body(Map.of("completo", false, "mensaje", "Complete sus datos para continuar")));
    }

    @PutMapping("/mi-perfil")
    public ResponseEntity<Usuario> actualizarMiPerfil(@RequestBody UpdateClienteRequest request, Authentication auth) {
        Usuario usuario = usuarioRepository.findByCorreo(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getIdentificacion() != null) usuario.setIdentificacion(request.getIdentificacion());
        if (request.getTelefono() != null) usuario.setTelefono(request.getTelefono());
        if (request.getDireccion() != null) usuario.setDireccion(request.getDireccion());
        if (request.getCiudad() != null) usuario.setCiudad(request.getCiudad());
        if (request.getRegion() != null) usuario.setRegion(request.getRegion());
        if (request.getReferencia() != null) usuario.setReferencia(request.getReferencia());

        usuario.setDatosCompletos(true);
        return ResponseEntity.ok(usuarioRepository.save(usuario));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "pdf") String formato) {
        List<Usuario> clientes = usuarioRepository.findAll().stream()
                .filter(u -> "CLIENTE".equals(u.getRol()))
                .toList();

        if ("xlsx".equalsIgnoreCase(formato) || "excel".equalsIgnoreCase(formato)) {
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Clientes");
                CellStyle hStyle = wb.createCellStyle();
                org.apache.poi.ss.usermodel.Font hFont = wb.createFont();
                hFont.setBold(true); hStyle.setFont(hFont);
                hStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                String[] cols = {"Nombre", "Identificaci\u00f3n", "Tel\u00e9fono", "Correo", "Direcci\u00f3n", "Ciudad"};
                Row header = sheet.createRow(0);
                for (int i = 0; i < cols.length; i++) {
                    Cell c = header.createCell(i);
                    c.setCellValue(cols[i]); c.setCellStyle(hStyle);
                }

                int rowNum = 1;
                for (Usuario c : clientes) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(c.getNombre() != null ? c.getNombre() : "\u2014");
                    row.createCell(1).setCellValue(c.getIdentificacion() != null ? c.getIdentificacion() : "\u2014");
                    row.createCell(2).setCellValue(c.getTelefono() != null ? c.getTelefono() : "\u2014");
                    row.createCell(3).setCellValue(c.getCorreo() != null ? c.getCorreo() : "\u2014");
                    row.createCell(4).setCellValue(c.getDireccion() != null ? c.getDireccion() : "\u2014");
                    row.createCell(5).setCellValue(c.getCiudad() != null ? c.getCiudad() : "\u2014");
                }
                for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                wb.write(baos);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Clientes-DriveMaster.xlsx\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(baos.toByteArray());
            } catch (Exception e) {
                throw new RuntimeException("Error al exportar clientes a Excel", e);
            }
        } else {
            try {
                byte[] pdf = PdfClienteBuilder.construir(clientes);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Clientes-DriveMaster.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdf);
            } catch (Exception e) {
                throw new RuntimeException("Error al exportar clientes a PDF", e);
            }
        }
    }

    @PutMapping("/mi-perfil/nombre")
    public ResponseEntity<Map<String, String>> actualizarMiNombre(@RequestBody Map<String, String> body, Authentication auth) {
        Usuario usuario = usuarioRepository.findByCorreo(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String nuevoNombre = body.get("nombre");
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new RuntimeException("El nombre no puede estar vacío");
        }
        usuario.setNombre(nuevoNombre);
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(Map.of("nombre", nuevoNombre));
    }
}
