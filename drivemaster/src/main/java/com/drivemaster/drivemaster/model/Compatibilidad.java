package com.drivemaster.drivemaster.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Compatibilidad {

    private String marca;
    private String modelo;
    private Integer anoDesde;
    private Integer anoHasta;

    // Opcional: si se indica, el vehículo debe tener el mismo motor (ej. "1.2", "2.0 TSI").
    private String motor;

    public Compatibilidad(String marca, String modelo, Integer anoDesde, Integer anoHasta) {
        this(marca, modelo, anoDesde, anoHasta, null);
    }
}

