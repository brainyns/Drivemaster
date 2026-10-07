package com.drivemaster.drivemaster.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Registro público de un taller o una tienda. Crea el usuario de acceso y el
 * negocio en estado PENDIENTE hasta que el ADMIN lo aprueba.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroNegocioRequest {

    // Negocio
    private String nombreNegocio;
    private String nit;
    private String telefono;
    private String ciudad;
    private String barrio;
    private String direccion;
    private Double latitud;
    private Double longitud;

    // Solo tiendas: si hacen envíos y a qué costo fijo (null/false = solo retiro)
    private Boolean ofreceEnvio;
    private Double costoEnvio;

    // Solo talleres
    private List<String> especialidades;
    private Double precioManoObra;
    private Double tarifaHora;
    private String horario;

    // Cuenta de acceso
    private String nombreContacto;
    private String correo;
    private String password;
}
