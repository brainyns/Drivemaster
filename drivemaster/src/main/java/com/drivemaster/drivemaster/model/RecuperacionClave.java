package com.drivemaster.drivemaster.model;

import com.drivemaster.drivemaster.service.CodigoVerificacion;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Recuperación de contraseña en curso, por correo. También se guarda cuando el correo no tiene cuenta con contraseña
 * (usuarioId null, con un código que nunca se envía): así la respuesta es la misma exista o no la cuenta, también al
 * escribir el código. Lo maneja RecuperacionClaveService.
 */
@Document(collection = "recuperaciones_clave")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecuperacionClave implements CodigoVerificacion.Pendiente {

    @Id
    private String id;

    // Indexado por IndicesMongo, con un índice TTL en "vence" que borra los olvidados
    private String correo;

    private String usuarioId;

    /** Hash del código (BCrypt). */
    private String codigoHash;

    private Instant vence;
    private int intentos;
    private Instant creado;
}
