package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.util.CalculadoraCotizacion;
import com.drivemaster.drivemaster.util.PdfCotizacionBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La misma pieza del taller (no responsable de IVA, $34.510) y de la tienda ($29.000 + IVA 19 %) le cuesta lo mismo al
 * cliente: el buscador y la cotización lo muestran así, sin cambiar el total.
 */
class PrecioFinalRepuestoTest {

    private static final Vehiculo SPARK = Vehiculo.builder().id("v1").marca("Chevrolet").modelo("Spark").anio(2018).build();
    private static final Taller NO_RESPONSABLE = Taller.builder().id("t1").responsableIva(false).build();

    private static PiezaTaller pieza() {
        return PiezaTaller.builder().id("t1|REF:C1").tallerId("t1").clave("REF:C1").nombre("Cilindro").referencia("C1")
                .compatibilidadUniversal(true).stock(5).precioVenta(34_510.0)
                .lotes(new ArrayList<>(List.of(PiezaTaller.Lote.builder().id("l1").cantidad(5).disponible(5).build()))).build();
    }

    private static Producto producto() {
        Producto p = new Producto();
        p.setId("p1");
        p.setNombre("Cilindro");
        p.setReferencia("C1");
        p.setTipo("STOCK");
        p.setStockActual(3);
        p.setPrecioVenta(29_000.0);
        p.setTiendaId("s1");
        p.setCompatibilidadUniversal(true);
        return p;
    }

    @Test
    void enElBuscadorLaMismaPiezaTieneElMismoPrecioFinal() {
        OfertaRepuestoDTO taller = BuscadorRepuestosService.conIva(
                BuscadorRepuestosService.evaluarPieza(pieza(), 1, SPARK, 34_510.0), 0);
        OfertaRepuestoDTO tienda = BuscadorRepuestosService.conIva(
                BuscadorRepuestosService.evaluar(producto(), Tienda.builder().id("s1").nombre("La Vaquita").build(), 1, NO_RESPONSABLE, SPARK), 19);

        assertEquals(34_510.0, taller.getPrecioFinal());
        assertEquals(34_510.0, tienda.getPrecioFinal());
        assertEquals(0.0, taller.getIvaMonto());
        assertEquals(29_000.0, tienda.getPrecio());
        assertEquals(5_510.0, tienda.getIvaMonto());
    }

    @Test
    void entreTiendasElOrdenSigueLoQuePagaElCliente() {
        OfertaRepuestoDTO cara = BuscadorRepuestosService.conIva(OfertaRepuestoDTO.builder().seleccionable(true).disponibilidad(OfertaRepuestoDTO.DISPONIBLE).precio(29_000.0).build(), 19);
        OfertaRepuestoDTO barata = BuscadorRepuestosService.conIva(OfertaRepuestoDTO.builder().seleccionable(true).disponibilidad(OfertaRepuestoDTO.DISPONIBLE).precio(30_000.0).build(), 0);
        // Base más alta pero final más bajo: el orden sigue lo que paga el cliente
        assertTrue(BuscadorRepuestosService.RECOMENDACION.compare(barata, cara) < 0);
    }

    private static Cotizacion cotizacion(List<CotizacionItem> items) {
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(items, 19, 0, 0, 10);
        Cotizacion c = new Cotizacion();
        c.setItems(items);
        c.setIvaRepuestosPct(19.0);
        c.setIvaManoObraPct(0.0);
        c.setTotal(t.total());
        return c;
    }

    private static CotizacionItem delTaller() {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).origen(CotizacionItem.ORIGEN_TALLER).piezaTallerId("t1|REF:C1")
                .descripcion("Cilindro").cantidad(5).precioUnitario(34_510.0).build();
    }

    private static CotizacionItem deTienda() {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).origen(CotizacionItem.ORIGEN_TIENDA).productoId("p1")
                .descripcion("Cilindro").cantidad(1).precioUnitario(29_000.0).build();
    }

    @Test
    void enLaCotizacionCadaLineaAclaraSuPrecioFinalYElTotalNoCambia() {
        Cotizacion c = cotizacion(List.of(delTaller(), deTienda()));

        CalculadoraCotizacion.PrecioFinal t = CalculadoraCotizacion.precioFinal(c, c.getItems().get(0));
        CalculadoraCotizacion.PrecioFinal s = CalculadoraCotizacion.precioFinal(c, c.getItems().get(1));
        assertEquals(t.total(), s.total());
        assertEquals(34_510.0, s.total());
        assertTrue(t.sinIva());
        // 5 × 34.510 sin IVA + 29.000 + 5.510 de IVA: lo mismo que 6 × 34.510
        assertEquals(6 * 34_510.0, c.getTotal());

        assertTrue(PdfCotizacionBuilder.aclaracionIva(t).endsWith("c/u, sin IVA"));
        String tienda = PdfCotizacionBuilder.aclaracionIva(s);
        assertTrue(tienda.contains("c/u con IVA") && tienda.contains("29.000") && tienda.contains("5.510"), tienda);
    }

    @Test
    void conUnSoloIvaNoHaceFaltaAclarar() {
        Cotizacion c = cotizacion(List.of(deTienda()));
        c.setIvaManoObraPct(19.0);
        assertNull(CalculadoraCotizacion.precioFinal(c, c.getItems().get(0)));
        CotizacionItem mano = CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).precioUnitario(50_000.0).build();
        c.setIvaManoObraPct(0.0);
        assertNull(CalculadoraCotizacion.precioFinal(c, mano));
    }
}
