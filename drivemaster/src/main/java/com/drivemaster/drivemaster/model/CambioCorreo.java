package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Cambio de correo en curso: el código de 6 dígitos que se envió al correo nuevo (solo su hash), cuándo vence y
 * cuántas veces se ha intentado. Uno por usuario; se borra al confirmar, al vencer, al agotar los intentos o al
 * cancelar. Lo maneja CambioCorreoService.
 */
@Document(collection = "cambios_correo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambioCorreo {

    @Id
    private String id;

    // Indexado por IndicesMongo, con un índice TTL en "vence" que borra los olvidados
    private String usuarioId;

    private String correoNuevo;

    /** Hash del código (BCrypt): si la base se filtra, el código no sirve. */
    private String codigoHash;

    private Instant vence;
    private int intentos;
    private Instant creado;
}
