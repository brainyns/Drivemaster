package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Vehiculo;

/**
 * Regla única de compatibilidad repuesto ↔ vehículo: marca y modelo iguales, año
 * dentro del rango y, si ambos lo indican, el mismo motor.
 */
public final class CompatibilidadVehiculo {

    /** Universal o con compatibilidad declarada para el vehículo. */
    public static final String COMPATIBLE = "COMPATIBLE";
    /** Declara compatibilidades, pero ninguna es la del vehículo. */
    public static final String NO_COMPATIBLE = "NO_COMPATIBLE";
    /** No declara compatibilidades ni es universal: el mecánico debe confirmarla. */
    public static final String POR_CONFIRMAR = "POR_CONFIRMAR";

    private CompatibilidadVehiculo() {
    }

    public static String estado(Producto producto, Vehiculo vehiculo) {
        if (Boolean.TRUE.equals(producto.getCompatibilidadUniversal())) return COMPATIBLE;
        if (esCompatible(producto, vehiculo)) return COMPATIBLE;
        if (sinDatos(producto)) return POR_CONFIRMAR;
        return NO_COMPATIBLE;
    }

    /** El producto declara compatibilidad explícita con el vehículo. */
    public static boolean esCompatible(Producto producto, Vehiculo vehiculo) {
        if (producto.getModelosCompatibles() == null) return false;
        for (Compatibilidad c : producto.getModelosCompatibles()) {
            boolean marcaOk = c.getMarca() != null && c.getMarca().equalsIgnoreCase(vehiculo.getMarca());
            boolean modeloOk = c.getModelo() != null && c.getModelo().equalsIgnoreCase(vehiculo.getModelo());
            if (!marcaOk || !modeloOk) continue;

            Integer desde = c.getAnoDesde();
            Integer hasta = c.getAnoHasta();
            Integer anioVehiculo = vehiculo.getAnio();
            boolean anioOk = (desde == null || anioVehiculo == null || anioVehiculo >= desde)
                    && (hasta == null || anioVehiculo == null || anioVehiculo <= hasta);
            // El motor solo restringe si ambos lo indican.
            boolean motorOk = c.getMotor() == null || c.getMotor().isBlank()
                    || vehiculo.getMotor() == null || vehiculo.getMotor().isBlank()
                    || c.getMotor().trim().equalsIgnoreCase(vehiculo.getMotor().trim());
            if (anioOk && motorOk) return true;
        }
        return false;
    }

    /** Sin compatibilidades declaradas. */
    public static boolean sinDatos(Producto producto) {
        return producto.getModelosCompatibles() == null || producto.getModelosCompatibles().isEmpty();
    }

    /** "Chevrolet Spark GT 2018" para los mensajes. */
    public static String describir(Vehiculo vehiculo) {
        return (vehiculo.getMarca() + " " + vehiculo.getModelo()
                + (vehiculo.getAnio() != null ? " " + vehiculo.getAnio() : "")).trim();
    }
}
