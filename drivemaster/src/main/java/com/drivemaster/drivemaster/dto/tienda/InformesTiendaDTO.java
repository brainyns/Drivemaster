package com.drivemaster.drivemaster.dto.tienda;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Informes de la TIENDA sobre su propia operación. Cada tienda recibe solo lo suyo (el filtro por dueño está en el
 * servicio) y sin datos de clientes: ni nombres, ni teléfonos, ni direcciones.
 */
public final class InformesTiendaDTO {

    private InformesTiendaDTO() {
    }

    public record MesVenta(String mes, long pedidos, double ventas) {}

    /** Ventas de la tienda por mes en hora de Colombia: pedidos que no quedaron sin despachar ni sin entregar. */
    public record VentasPorMes(List<MesVenta> meses, long pedidos, double ventas) {}

    public record ProductoVendido(String productoId, String nombre, long unidades, double ventas) {}

    public record ProductoBajo(String productoId, String codigo, String nombre, int stock, int minimo) {}

    /** total: cuántos productos están en o bajo su mínimo; items: los más urgentes (hasta 20). */
    public record StockBajo(long total, List<ProductoBajo> items) {}

    /**
     * Tiempo desde el pago hasta que la tienda despacha (envío) o deja listo el pedido (retiro), en horas. porcentajeATiempo
     * es el de los despachados dentro del plazo. Los pendientes son lo que hoy está esperando a la tienda.
     */
    public record Despacho(long despachados, Double horasPromedio, Double porcentajeATiempo, long pendientes, long atrasados) {}

    /** estado del reparto → cuántos y cuánto. En espera es dinero que espera la entrega; por liquidar ya se puede pagar. */
    public record Liquidacion(double enEspera, long lineasEnEspera, double porLiquidar, long lineasPorLiquidar,
                              double liquidado, long lineasLiquidadas, double anulado) {}

    public record Informes(LocalDateTime generadoEn, int mesesConsultados, VentasPorMes ventas,
                           List<ProductoVendido> productosMasVendidos, StockBajo stockBajo,
                           Map<String, Long> pedidosPorEstado, Despacho despacho, Liquidacion liquidacion) {}
}
