package com.drivemaster.drivemaster.model;

/**
 * Estado de revisión de una cuenta de negocio (taller o tienda). El ADMIN valida
 * y activa cada cuenta antes de que pueda operar en el marketplace.
 */
public final class EstadoAprobacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    // Fuera de servicio sin borrar nada (p. ej. la antigua tienda DriveMaster o un duplicado): no opera, no vende ni
    // cuenta en los indicadores, y su historial (pedidos, órdenes, movimientos) se conserva.
    public static final String ARCHIVADO = "ARCHIVADO";

    private EstadoAprobacion() {
    }

    /** Los registros creados antes del marketplace no tienen estado y se consideran aprobados. Archivado no lo está. */
    public static boolean estaAprobado(String estado) {
        return estado == null || APROBADO.equals(estado);
    }

    public static boolean estaArchivado(String estado) {
        return ARCHIVADO.equals(estado);
    }
}
