package com.drivemaster.drivemaster.dto.taller;

import com.drivemaster.drivemaster.model.Compatibilidad;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "Mi inventario". Lleva costo y margen: solo sale por /api/inventario-taller, que exige el rol TALLER y filtra por el
 * taller de la sesión. Ninguna respuesta para el cliente usa estas clases.
 */
public final class InventarioTallerDTO {

    private InventarioTallerDTO() {
    }

    public record Inventario(List<Pieza> piezas, List<PedidoEnCamino> enCamino, double comisionPct, boolean comisionSeDescuenta,
                             double recargoSugeridoPct, double valorACosto, double valorAVenta) {
    }

    /**
     * costoUnitario es el del próximo lote en salir (FIFO). recargoPct es sobre el costo, margenPct sobre el precio de
     * venta y gananciaDespuesComision queda por unidad, antes de IVA. enPerdida avisa, no bloquea.
     */
    public record Pieza(String id, String nombre, String marca, String referencia, String categoria, String imagenUrl,
                        int stock, Double precioVenta, Double costoUnitario, Double recargoPct, Double margenPct,
                        Double comision, Double gananciaDespuesComision, boolean enPerdida, Boolean compatibilidadUniversal,
                        List<Compatibilidad> modelosCompatibles, List<Lote> lotes) {
    }

    public record Lote(String id, String compraId, String pedidoId, String tiendaNombre, int cantidad, int disponible,
                       Double costoUnitario, Boolean ivaEnCosto, LocalDateTime fechaEntrada) {
    }

    /** Pedido de una compra del taller que aún no entra al inventario. puedeConfirmar: ya salió de la tienda. */
    public record PedidoEnCamino(String id, String compraId, String tiendaNombre, String entrega, String estado,
                                 boolean puedeConfirmar, boolean problemaReportado, boolean puedeReportar, boolean puedePedirCodigo,
                                 String tiendaDireccion, String tiendaCiudad, String tiendaHorario,
                                 LocalDateTime fechaCreacion, List<LineaEnCamino> lineas) {
    }

    public record LineaEnCamino(String nombre, String marca, String referencia, int cantidad, Double precioUnitario) {
    }

    public record CambioPrecio(Double precioVenta) {
    }
}
