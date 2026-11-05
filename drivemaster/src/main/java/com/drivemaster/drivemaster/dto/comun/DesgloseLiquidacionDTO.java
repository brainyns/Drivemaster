package com.drivemaster.drivemaster.dto.comun;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve un negocio de su dinero: venta, comisión de DriveMaster y lo que recibe. Va sin notas internas, motivos de
 * fallo ni datos de cuentas. Venta es lo que le corresponde con IVA; la comisión sale de comisionBase, sin IVA.
 */
public final class DesgloseLiquidacionDTO {

    private DesgloseLiquidacionDTO() {
    }

    // Estado del pago para el negocio
    public static final String EN_ESPERA = "EN_ESPERA";
    public static final String LISTO = "LISTO";
    public static final String PAGADO = "PAGADO";
    // No movió dinero: pagarlo en pantalla sería decir algo falso
    public static final String SIMULADO = "SIMULADO";
    public static final String ANULADO = "ANULADO";

    // Qué pasó con la comisión de la línea
    public static final String DESCONTADA = "DESCONTADA";
    /** La bandera está apagada: "No se descuenta todavía". */
    public static final String TODAVIA_NO = "TODAVIA_NO";
    /** La bandera está encendida, pero este pago se aprobó antes: "No se descontó en este pago". */
    public static final String NO_EN_ESTE_PAGO = "NO_EN_ESTE_PAGO";

    /** comision es null en las líneas anteriores al desglose, que no la guardaron por línea. */
    public record Linea(String concepto, String estado, double venta, Double comisionPct, Double comisionBase, Double comision,
                        String estadoComision, double recibes, LocalDateTime fechaPago, String referencia) {}

    public record Desglose(boolean comisionSeDescuenta, List<Linea> lineas) {}

    /** Totales para los informes, sin lo anulado. comision: la que corresponde; comisionDescontada: la que sí se descontó. */
    public record Resumen(double venta, double comision, double comisionDescontada, double recibes, boolean comisionSeDescuenta) {

        public static Resumen de(List<Object[]> filas, boolean comisionSeDescuenta) {
            Object[] f = filas == null || filas.isEmpty() ? null : filas.get(0);
            if (f == null) return new Resumen(0, 0, 0, 0, comisionSeDescuenta);
            return new Resumen(numero(f[0]), numero(f[2]), numero(f[1]), numero(f[3]), comisionSeDescuenta);
        }

        private static double numero(Object o) {
            return o instanceof Number n ? n.doubleValue() : 0;
        }
    }
}
