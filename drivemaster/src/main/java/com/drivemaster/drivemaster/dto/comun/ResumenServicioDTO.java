package com.drivemaster.drivemaster.dto.comun;

import java.util.List;

/**
 * El dinero de una orden en un solo resumen. El diagnóstico y la cotización son dos pagos y dos líneas de reparto, y aquí
 * se leen juntos. Los montos llevan IVA, como la venta del desglose por línea; lo anulado no cuenta y los repuestos no
 * son del taller. Al cliente nunca le llega la comisión: su resumen es otro tipo.
 */
public final class ResumenServicioDTO {

    private ResumenServicioDTO() {
    }

    // Estado de toda la orden; los demás son los de DesgloseLiquidacionDTO, cuando todas sus líneas están igual
    public static final String SIN_PAGOS = "SIN_PAGOS";
    public static final String PARCIAL = "PARCIAL";

    /**
     * diagnosticoPagado + manoDeObra − anticipo = totalGenerado, y manoDeObra − anticipo = cotizacionPagada.
     * manoDeObra lleva su IVA. iva: la parte del total que es IVA; null en líneas anteriores al desglose, que no guardaron su base.
     * comision: la que corresponde; comisionDescontada: la que sí se descontó.
     */
    public record Taller(double diagnosticoPagado, double manoDeObra, double anticipo, double cotizacionPagada, double totalGenerado,
                         Double iva, double comision, double comisionDescontada, boolean comisionSeDescuenta, double recibe,
                         String estado, List<DesgloseLiquidacionDTO.Linea> lineas) {}

    /** Lo pagado. devolucionPendiente y devuelto se restan de total para saber con cuánto quedó el servicio. */
    public record Cliente(double diagnostico, double cotizacion, double total, double devolucionPendiente, double devuelto) {}

    /** Taller y cliente de la orden, más lo que fue a las tiendas por repuestos. */
    public record Admin(Taller taller, Cliente cliente, double repuestos, double comisionRepuestos) {}

    /** El total de una orden en una fila de Liquidaciones. lineas: las líneas del taller que suma, sin las anuladas. */
    public record Fila(double totalGenerado, double comision, double comisionDescontada, double recibe, String estado, int lineas) {}
}
