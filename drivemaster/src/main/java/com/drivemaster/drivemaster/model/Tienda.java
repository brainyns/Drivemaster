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
 * vende. DriveMaster es la aplicación, no una tienda: todas las tiendas son de un usuario TIENDA.
 */
@Document(collection = "tiendas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tienda {

    @Id
    private String id;

    // Usuario (rol TIENDA) que administra la tienda
    private String usuarioId;

    // Datos visibles para talleres y clientes
    private String nombre;
    private String ciudad;
    private String barrio;
    private String direccion;
    private Double latitud;
    private Double longitud;

    // Horario de atención al público (retiro de compras del catálogo). Obligatorio junto con dirección y ciudad.
    private String horario;
    // Costo fijo de envío de la tienda, en pesos. null = la tienda no hace envíos (solo retiro).
    private Double costoEnvio;

    // Logo o foto del local (URL de Cloudinary); lo sube la dueña desde Mi tienda o el administrador.
    private String logoUrl;

    // Datos administrativos (privados)
    private String nit;
    private String telefono;
    private String correoContacto;

    // PENDIENTE, APROBADO, RECHAZADO (ver EstadoAprobacion)
    private String estadoAprobacion;
    private String motivoRechazo;
    private Boolean activo;

    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaAprobacion;

    // Resumen semanal por correo (lunes 7:00): solo si el dueño lo activó en "Mi tienda".
    private Boolean resumenSemanal;
    // Lunes de la semana del último envío: evita repetirlo si el job corre dos veces.
    private java.time.LocalDate resumenSemanalEnviado;

    /**
     * Dirección, ciudad y horario son obligatorios para vender en el catálogo. Las tiendas
     * anteriores a la 4-A que no los tengan siguen aprobadas, pero sus productos no se
     * muestran hasta que los completen. No se persiste: se calcula con los datos reales.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean tieneDatosCompletos() {
        return noVacio(direccion) && noVacio(ciudad) && noVacio(horario);
    }

    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }
}
