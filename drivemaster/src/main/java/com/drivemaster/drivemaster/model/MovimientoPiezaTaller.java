package com.drivemaster.drivemaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rastro de cada cambio en el inventario de un taller. Una salida guarda de qué lote salió cada unidad y con qué costo,
 * y una reversa devuelve exactamente eso. El id es tipo|piezaId|referencia: repetir una operación no la aplica dos veces.
 */
@Document(collection = "movimientos_pieza_taller")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoPiezaTaller {

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";
    public static final String REVERSA = "REVERSA";
    // Conteo físico: el stock queda en lo contado; cantidad es la diferencia (el signo sale de stock antes → después)
    public static final String AJUSTE = "AJUSTE";

    // Por qué se movió: lo que ve el taller en el historial. Los movimientos anteriores al campo se deducen del tipo (claseDe)
    public static final String COMPRA = "COMPRA";
    public static final String VENTA = "VENTA";
    public static final String CANCELACION = "CANCELACION";
    public static final String CARGA_INICIAL = "CARGA_INICIAL";
    public static final String MERMA = "MERMA";
    public static final String DEVOLUCION_TIENDA = "DEVOLUCION_TIENDA";
    public static final String DEVOLUCION_CLIENTE = "DEVOLUCION_CLIENTE";

    @Id
    private String id;
    private String tallerId;
    private String piezaId;
    private String piezaNombre;
    private String tipo;
    private String clase;
    private int cantidad;
    private int stockAnterior;
    private int stockNuevo;
    // PAGO-<ref> en ventas y reversas, PEDIDO-<id>-<producto> en entradas
    private String referencia;
    private String motivo;

    private String ordenId;
    private String cotizacionId;
    private Long pagoId;
    private String pedidoId;
    // Salidas: a cuánto se vendió cada unidad, antes de IVA
    private Double precioVentaUnitario;
    @Builder.Default
    private List<ConsumoLote> lotes = new ArrayList<>();

    private LocalDateTime fecha;
    private String usuarioId;

    public static String idDe(String tipo, String piezaId, String referencia) {
        return tipo + "|" + piezaId + "|" + referencia;
    }

    /** La clase guardada o, en movimientos anteriores a ella, la que corresponde a su tipo. */
    public static String claseDe(MovimientoPiezaTaller m) {
        if (m.getClase() != null) return m.getClase();
        if (SALIDA.equals(m.getTipo())) return VENTA;
        if (REVERSA.equals(m.getTipo())) return REVERSA;
        if (AJUSTE.equals(m.getTipo())) return AJUSTE;
        return COMPRA;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConsumoLote {
        private String loteId;
        private int cantidad;
        private Double costoUnitario;
    }
}
