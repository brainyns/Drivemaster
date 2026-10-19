package com.drivemaster.drivemaster.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Reglas de una imagen subida por un usuario antes de mandarla a Cloudinary (CloudinaryService, el mismo destino de la
 * foto del vehículo). El tamaño máximo es el de la foto del vehículo (spring.servlet.multipart.max-file-size=5MB, y
 * los 5 MB que valida Mis vehículos); los tipos son las fotos comunes, sin SVG (puede llevar código).
 */
public final class ImagenSubida {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private ImagenSubida() {
    }

    public static void validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una imagen");
        }
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase();
        if (!TIPOS.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen debe ser JPG, PNG, WEBP o GIF");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen debe pesar menos de 5 MB");
        }
    }
}
