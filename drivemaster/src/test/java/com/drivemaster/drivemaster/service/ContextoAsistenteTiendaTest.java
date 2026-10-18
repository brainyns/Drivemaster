package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoBajo;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.Margen;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.Reponer;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.SinMovimiento;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextoAsistenteTiendaTest {

    private static Producto producto(String id, String tipo, Integer stock, Double compra, Double venta) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("P" + id);
        p.setTipo(tipo);
        p.setStockActual(stock);
        p.setPrecioCompra(compra);
        p.setPrecioVenta(venta);
        return p;
    }

    @Test
    void reponerCubre30DiasAlRitmoDe90YVuelveAlMinimo() {
        List<Reponer> r = ContextoAsistenteTienda.reponer(List.of(
                new ProductoBajo("a", "A1", "Pa", 2, 5),
                new ProductoBajo("b", "B1", "Pb", 5, 5)), Map.of("a", 10L));
        assertEquals(7, r.get(0).sugerido());   // ceil(10/3)=4 + 5 − 2
        assertEquals(0, r.get(1).sugerido());   // sin ventas y ya en el mínimo
    }

    @Test
    void sinMovimientoIgnoraEncargosAgotadosYVendidosYOrdenaPorCapital() {
        List<SinMovimiento> s = ContextoAsistenteTienda.sinMovimiento(List.of(
                producto("1", "STOCK", 3, 1000.0, 2000.0),
                producto("2", "STOCK", 1, 9000.0, 12000.0),
                producto("3", "ENCARGO", 5, 1000.0, 2000.0),
                producto("4", "STOCK", 0, 1000.0, 2000.0),
                producto("5", "STOCK", 4, 1000.0, 2000.0),
                producto("6", "STOCK", 2, null, 2000.0)), Map.of("5", 1L), Map.of());
        assertEquals(List.of("P2", "P1", "P6"), s.stream().map(SinMovimiento::producto).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void margenesSobreElPrecioDeVenta() {
        Map<String, Object> m = ContextoAsistenteTienda.margenes(List.of(
                producto("1", "STOCK", 1, 60.0, 100.0),
                producto("2", "STOCK", 1, 90.0, 100.0),
                producto("3", "STOCK", 1, null, 100.0)), Map.of("1", 2L));
        List<Margen> mas = (List<Margen>) m.get("masGanancia90d");
        List<Margen> bajo = (List<Margen>) m.get("margenMasBajo");
        assertEquals(1, mas.size());
        assertEquals(80.0, mas.get(0).ganancia90d());
        assertEquals(40.0, mas.get(0).margenPct());
        assertEquals("P2", bajo.get(0).producto());
        assertEquals(1, m.get("productosSinPrecioDeCompra"));
    }
}
