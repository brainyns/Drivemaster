package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Tienda de autopartes del marketplace. Sigue el mismo patrón que Taller: un
 * perfil de negocio enlazado al Usuario (rol TIENDA) que inicia sesión.
 *
 * El inventario no vive aquí: cada Producto lleva el tiendaId de la tienda que lo
 * vende. La tienda propia de DriveMaster es una Tienda más, marcada con plataforma=true.
 */
@Document(collection = "tiendas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tienda {

    @Id
    private String id;

    // Usuario (rol TIENDA) que administra la tienda. Vacío en la tienda de la plataforma,
    // que gestiona el personal interno de DriveMaster.
    private String usuarioId;

    // Datos visibles para talleres y clientes
    private String nombre;
    private String ciudad;
    private String barrio;
    private String direccion;
    private Double latitud;
    private Double longitud;

    // Datos administrativos (privados)
    private String nit;
    private String telefono;
    private String correoContacto;

    // PENDIENTE, APROBADO, RECHAZADO (ver EstadoAprobacion)
    private String estadoAprobacion;
    private String motivoRechazo;
    private Boolean activo;

    // true solo para la tienda propia de DriveMaster (catálogo de compra directa)
    private Boolean plataforma;

    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaAprobacion;
}
