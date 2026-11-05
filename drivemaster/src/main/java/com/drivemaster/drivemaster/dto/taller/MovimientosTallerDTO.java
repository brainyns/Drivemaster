package com.drivemaster.drivemaster.dto.taller;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.service.ReferenciasMovimiento;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Movimientos del inventario de un taller, como la pantalla "Inventario" de la tienda. Solo los ve su taller.
 *
 * tipo: ENTRADA, SALIDA, REVERSA o AJUSTE (el filtro junta ENTRADA y REVERSA en "Entradas"). clase: por qué se movió
 * (COMPRA, VENTA, CANCELACION, REVERSA, AJUSTE, CARGA_INICIAL, MERMA, DEVOLUCION_TIENDA, DEVOLUCION_CLIENTE).
 * cantidad va siempre positiva; si suma o resta lo dice stock antes → después.
 */
public final class MovimientosTallerDTO {

    private MovimientosTallerDTO() {
    }

    public record Fila(String id, String piezaId, String piezaNombre, String piezaReferencia, String imagenUrl,
                       LocalDateTime fecha, String tipo, String clase, int cantidad, int stockAnterior, int stockNuevo,
                       // De qué lotes salió o a cuáles volvió: "2 del lote del 12/10/2026 · La Vaquita"
                       List<String> lotes, String motivo, ReferenciasMovimiento.Referencia referencia, String usuario) {
    }

    public record Resumen(long total, long entradas, long salidas, long ajustes) {
    }

    public record Tabla(Pagina<Fila> movimientos, Resumen resumen) {
    }

    /**
     * Lo que registra el taller a mano. clase AJUSTE (cantidad = stock contado), MERMA, DEVOLUCION_TIENDA y
     * DEVOLUCION_CLIENTE llevan piezaId; CARGA_INICIAL lleva productoId (la pieza del catálogo) y costo por unidad.
     */
    public record Registro(String clase, String piezaId, String productoId, Integer cantidad, Double costo, String motivo) {
    }

    public record Minimo(Integer minimo) {
    }
}
