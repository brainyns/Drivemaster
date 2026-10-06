package com.drivemaster.drivemaster.model;

/**
 * Estado de revisión de una cuenta de negocio (taller o tienda). El ADMIN valida
 * y activa cada cuenta antes de que pueda operar en el marketplace.
 */
public final class EstadoAprobacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";

    private EstadoAprobacion() {
    }

    /** Los registros creados antes del marketplace no tienen estado y se consideran aprobados. */
    public static boolean estaAprobado(String estado) {
        return estado == null || APROBADO.equals(estado);
    }
}
