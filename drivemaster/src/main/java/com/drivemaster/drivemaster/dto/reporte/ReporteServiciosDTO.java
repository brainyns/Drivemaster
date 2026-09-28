package com.drivemaster.drivemaster.dto.reporte;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Reporte de servicios de taller (órdenes de servicio).
 *
 * <p>Las órdenes se miden por su estado real, no por la venta: la venta queda en
 * PAGADA desde el cobro, mientras que la orden sigue su ciclo
 * COTIZADA → PAGADA → EN_PROCESO → FINALIZADA.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteServiciosDTO {

    private long totalOrdenes;
    private long ordenesFinalizadas;
    private long ordenesEnProceso;
    private long ordenesCanceladas;

    private double ingresosTotales;

    /** Promedio cobrado por orden efectivamente facturada. */
    private double ticketPromedio;

    /** Concentración de órdenes: qué talleres prefieren los clientes. */
    private List<TallerRankingDTO> talleresPreferidos;

    /** Qué repuestos llevan más los clientes al taller. */
    private List<ProductoRankingDTO> productosMasSolicitados;

    /** Citas de ingreso del vehículo ya agendadas para una fecha futura. */
    private long citasAgendadas;

    /** Órdenes que esperan a ser iniciadas por el taller. */
    private long citasPendientes;

    /** Órdenes del periodo que ya se cobraron, sin importar en qué punto estén. */
    private long ordenesFacturadas;

    /** Órdenes que se quedaron quietas (cotizada sin pagar, pagada sin iniciar o
     * en proceso sin finalizar). Coinciden con el flag {@code retrasada} del
     * OrdenServicioDTO. */
    private long ordenesRetrasadas;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TallerRankingDTO {
        private String tallerId;
        private String nombre;
        private long ordenes;
        private long finalizadas;
        /** Porcentaje sobre las órdenes vigentes del periodo (sin canceladas). */
        private double participacion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductoRankingDTO {
        private String productoId;
        private String nombre;
        private long unidades;
        private long ordenes;
    }
}
