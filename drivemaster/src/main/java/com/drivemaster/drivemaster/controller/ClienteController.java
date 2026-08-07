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

import com.drivemaster.drivemaster.dto.UpdateClienteRequest;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.PdfClienteBuilder;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/clientes")
public class ClienteController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ClienteController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/guardar")
    public ResponseEntity<Usuario> guardar(@RequestBody Usuario usuario) {
        if (usuario.getCorreo() == null || usuario.getCorreo().isBlank()) {
            throw new RuntimeException("El correo es obligatorio");
        }
        if (usuarioRepository.existsByCorreo(usuario.getCorreo())) {
            throw new RuntimeException("El correo ya está registrado");
        }
        usuario.setRol("CLIENTE");
        usuario.setProveedor("LOCAL");
        if (usuario.getPassword() != null && !usuario.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        }
        usuario.setActivo(true);
        usuario.setDatosCompletos(true);
        usuario.setFechaCreacion(Instant.now());
        usuario.setIntentosFallidos(0);
        usuario.setBloqueado(false);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioRepository.save(usuario));
    }

    @GetMapping
    public List<Usuario> listarClientes() {
        return usuarioRepository.findAll().stream()
                .filter(u -> "CLIENTE".equals(u.getRol()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> actualizar(@PathVariable String id, @RequestBody Usuario usuario) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (usuario.getNombre() != null) existente.setNombre(usuario.getNombre());
        if (usuario.getIdentificacion() != null) existente.setIdentificacion(usuario.getIdentificacion());
        if (usuario.getTelefono() != null) existente.setTelefono(usuario.getTelefono());
        if (usuario.getDireccion() != null) existente.setDireccion(usuario.getDireccion());
        if (usuario.getCiudad() != null) existente.setCiudad(usuario.getCiudad());
        if (usuario.getRegion() != null) existente.setRegion(usuario.getRegion());
        if (usuario.getReferencia() != null) existente.setReferencia(usuario.getReferencia());
        return ResponseEntity.ok(usuarioRepository.save(existente));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        usuarioRepository.deleteById(id);
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
