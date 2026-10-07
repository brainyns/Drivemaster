package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Un repuesto del buscador con sus ofertas en todas las tiendas
 * (ej. Tienda A: 8 uds, Tienda B: 3, Tienda C: 0), ya ordenadas por
 * disponibilidad → distancia → precio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepuestoBuscadoDTO {
    private String clave;
    private String nombre;
    private String marca;
    private String referencia;
    private String categoria;
    private String imagenUrl;

    private int stockTotal;
    private int tiendasConStock;
    private List<OfertaRepuestoDTO> ofertas;
}
