package com.drivemaster.drivemaster.dto.tienda;

import java.util.Map;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;

/**
 * Las tablas paginadas del panel de la tienda. Los contadores de las pestañas y los indicadores cuentan todos los
 * registros de la tienda (como antes en el navegador), no solo la página ni el filtro elegido.
 */
public final class TablasTiendaDTO {

    private TablasTiendaDTO() {
    }

    /** valor y valorCosto: el stock a precio de venta y de compra. atencion: agotados y en el mínimo. */
    public record ResumenInventario(long total, long publicados, long atencion, double valor, double valorCosto) {}

    /** conteo: productos por pestaña (TODOS, POCO, AGOTADOS, PUBLICADOS, OCULTOS). */
    public record Inventario(Pagina<Producto> productos, Map<String, Long> conteo, ResumenInventario resumen) {}

    /** conteo: pedidos por pestaña (ATENDER, PREPARACION, LISTOS, EN_CAMINO, NUEVOS, ENTREGADOS, CANCELADOS, TODOS). */
    public record Pedidos(Pagina<PedidoTienda> pedidos, Map<String, Long> conteo) {}

    /** De todos los movimientos de la tienda: cuántos, unidades que entraron y salieron, y cuántos ajustes. */
    public record ResumenMovimientos(long total, long entradas, long salidas, long ajustes) {}

    public record Movimientos(Pagina<MovimientoInventario> movimientos, ResumenMovimientos resumen) {}
}
