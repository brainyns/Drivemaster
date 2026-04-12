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
}

