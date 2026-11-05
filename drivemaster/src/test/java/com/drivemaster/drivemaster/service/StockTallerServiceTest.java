package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PiezaTaller;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/** FIFO con rastro, reversa exacta al lote de origen y repeticiones que no aplican dos veces. */
class StockTallerServiceTest {

    private static final String TALLER = "t1";
    private static final String PIEZA = PiezaTaller.idDe(TALLER, "REF:BP1");

    private final InventarioEnMemoria inventario = new InventarioEnMemoria();
    private final StockTallerService stock = inventario.stock;

    private void entrada(String pedido, int cantidad, double costo, int diasAtras) {
        PiezaTaller datos = PiezaTaller.builder().clave("REF:BP1").nombre("Pastillas").marca("Bosch").build();
        stock.entrar(new StockTallerService.Entrada(TALLER, datos, PiezaTaller.Lote.builder().id(pedido + "|p1").pedidoId(pedido)
                .productoId("p1").cantidad(cantidad).disponible(cantidad).costoUnitario(costo)
                .fechaEntrada(LocalDateTime.now().minusDays(diasAtras)).build(), "u1"));
    }

    private MovimientoPiezaTaller vender(int cantidad, String referencia) {
        return stock.descontar(new StockTallerService.Venta(TALLER, PIEZA, cantidad, referencia, 150_000, "o1", "c1", 9L, "cl1"));
    }

    @Test
    void la_primera_entrada_propone_el_recargo_y_las_siguientes_no_cambian_el_precio_del_taller() {
        entrada("ped1", 5, 100_000, 3);
        assertEquals(135_000, inventario.piezas.get(PIEZA).getPrecioVenta());
        assertEquals(5, inventario.piezas.get(PIEZA).getStock());

        entrada("ped2", 2, 200_000, 1);
        assertEquals(135_000, inventario.piezas.get(PIEZA).getPrecioVenta());
        assertEquals(7, inventario.piezas.get(PIEZA).getStock());
    }

    @Test
    void el_mismo_pedido_no_entra_dos_veces() {
        entrada("ped1", 5, 100_000, 3);
        entrada("ped1", 5, 100_000, 3);
        assertEquals(5, inventario.piezas.get(PIEZA).getStock());
        assertEquals(1, inventario.piezas.get(PIEZA).getLotes().size());
    }

    @Test
    void riesgo3_la_salida_usa_el_lote_mas_antiguo_y_registra_lote_y_costo() {
        entrada("nuevo", 3, 120_000, 1);
        entrada("viejo", 2, 100_000, 9);

        MovimientoPiezaTaller salida = vender(3, "PAGO-1");

        assertEquals(List.of(
                MovimientoPiezaTaller.ConsumoLote.builder().loteId("viejo|p1").cantidad(2).costoUnitario(100_000.0).build(),
                MovimientoPiezaTaller.ConsumoLote.builder().loteId("nuevo|p1").cantidad(1).costoUnitario(120_000.0).build()),
                salida.getLotes());
        assertEquals(5, salida.getStockAnterior());
        assertEquals(2, salida.getStockNuevo());
        assertEquals(150_000, salida.getPrecioVentaUnitario());
        assertEquals(Map.of("nuevo|p1", 2, "viejo|p1", 0), inventario.disponiblePorLote(PIEZA));
    }

    @Test
    void si_no_alcanza_falla_sin_tocar_nada() {
        entrada("ped1", 2, 100_000, 1);
        assertThrows(StockInsuficienteException.class, () -> vender(3, "PAGO-1"));
        assertEquals(2, inventario.piezas.get(PIEZA).getStock());
        assertEquals(1, inventario.movimientos.size());
    }

    @Test
    void repetir_la_venta_no_descuenta_otra_vez() {
        entrada("ped1", 5, 100_000, 1);
        vender(2, "PAGO-1");
        vender(2, "PAGO-1");
        assertEquals(3, inventario.piezas.get(PIEZA).getStock());
    }

    @Test
    void la_pieza_de_otro_taller_no_se_puede_vender() {
        entrada("ped1", 5, 100_000, 1);
        assertThrows(StockInsuficienteException.class, () -> stock.descontar(
                new StockTallerService.Venta("otro", PIEZA, 1, "PAGO-1", 1, "o1", "c1", 9L, "cl1")));
        assertEquals(0, stock.disponible("otro", PIEZA));
    }

    @Test
    void si_otra_venta_gano_se_repite_con_el_stock_nuevo() {
        entrada("ped1", 5, 100_000, 1);
        // Entre leer y guardar, otra venta se lleva una unidad: esta escritura pierde y debe repetirse
        inventario.antesDeGuardar = () -> {
            PiezaTaller ganadora = InventarioEnMemoria.copia(inventario.piezas.get(PIEZA));
            ganadora.setStock(4);
            ganadora.getLotes().get(0).setDisponible(4);
            ganadora.setVersion(ganadora.getVersion() + 1);
            inventario.piezas.put(PIEZA, ganadora);
        };

        vender(2, "PAGO-1");

        assertEquals(2, inventario.piezas.get(PIEZA).getStock());
        assertEquals(2, inventario.disponiblePorLote(PIEZA).get("ped1|p1"));
    }

    @Test
    void riesgo4_la_reversa_devuelve_a_cada_lote_lo_que_salio_de_el() {
        entrada("viejo", 2, 100_000, 9);
        entrada("nuevo", 3, 120_000, 1);
        vender(3, "PAGO-1");
        vender(1, "PAGO-2");
        assertEquals(Map.of("viejo|p1", 0, "nuevo|p1", 1), inventario.disponiblePorLote(PIEZA));

        assertEquals(1, stock.revertir("PAGO-1", "Orden cancelada", "admin"));

        assertEquals(Map.of("viejo|p1", 2, "nuevo|p1", 2), inventario.disponiblePorLote(PIEZA));
        assertEquals(4, inventario.piezas.get(PIEZA).getStock());
        MovimientoPiezaTaller reversa = inventario.movimientos.get(
                MovimientoPiezaTaller.idDe(MovimientoPiezaTaller.REVERSA, PIEZA, "PAGO-1"));
        assertEquals(1, reversa.getStockAnterior());
        assertEquals(4, reversa.getStockNuevo());
        assertEquals("Orden cancelada", reversa.getMotivo());
    }

    @Test
    void revertir_dos_veces_no_suma_dos_veces() {
        entrada("ped1", 5, 100_000, 1);
        vender(2, "PAGO-1");
        assertEquals(1, stock.revertir("PAGO-1", "a", "admin"));
        assertEquals(0, stock.revertir("PAGO-1", "b", "admin"));
        assertEquals(5, inventario.piezas.get(PIEZA).getStock());
    }

    @Test
    void sin_rastro_no_hay_venta_las_unidades_vuelven() {
        entrada("ped1", 5, 100_000, 1);
        org.mockito.Mockito.when(inventario.movimientoRepository.insert(any(MovimientoPiezaTaller.class)))
                .thenThrow(new IllegalStateException("Mongo caído"));

        assertThrows(IllegalStateException.class, () -> vender(2, "PAGO-1"));

        assertEquals(5, inventario.piezas.get(PIEZA).getStock());
        assertEquals(5, inventario.disponiblePorLote(PIEZA).get("ped1|p1"));
    }
}
