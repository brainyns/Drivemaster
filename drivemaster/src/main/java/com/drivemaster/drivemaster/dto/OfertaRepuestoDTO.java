package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Oferta de un repuesto en una tienda, tal como la ve el taller en el buscador.
 * Solo datos públicos de la tienda (nombre y ubicación): NIT, teléfono y demás
 * datos administrativos quedan privados.
 *
 * Todo lo que decide si se puede elegir (estado, seleccionable, motivo) lo
 * calcula el backend; la pantalla solo lo muestra.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfertaRepuestoDTO {

    // Disponibilidad
    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String ENCARGO = "ENCARGO";
    public static final String PARCIAL = "PARCIAL";
    public static final String AGOTADO = "AGOTADO";

    private String productoId;
    private String codigo;
    private String nombre;
    private String marca;
    private String referencia;
    private String imagenUrl;

    private String tiendaId;
    private String tiendaNombre;
    private String tiendaCiudad;
    private String tiendaBarrio;
    private Double distanciaKm;

    private Double precio;
    private Integer stock;

    // DISPONIBLE (alcanza la cantidad), ENCARGO, PARCIAL (hay menos de lo pedido) o AGOTADO
    private String disponibilidad;
    private Integer diasEntrega;

    // COMPATIBLE, NO_COMPATIBLE o POR_CONFIRMAR (ver CompatibilidadVehiculo)
    private String compatibilidad;

    /**
     * Etiqueta de la tarjeta. Si aplican varias gana la primera de:
     * NO_COMPATIBLE → AGOTADO → POR_CONFIRMAR → COMPATIBLE.
     */
    private String estado;

    // Se puede elegir: no es NO_COMPATIBLE y está DISPONIBLE o por ENCARGO
    private boolean seleccionable;

    // POR_CONFIRMAR: al elegirlo el mecánico debe confirmar la compatibilidad
    private boolean requiereConfirmacion;

    // Por qué no se puede elegir, o la advertencia si requiere confirmación
    private String motivo;

    // La tienda ya surte otros repuestos de esta orden (se intenta primero una sola tienda)
    private boolean mismaTiendaQueSeleccion;
}
