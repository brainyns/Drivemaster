package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CotizacionDTO;
import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.RepuestoBuscadoDTO;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.util.CalculadoraCotizacion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Las piezas del inventario frente al buscador, el IVA y lo que el cliente puede ver de ellas. */
class PiezasTallerEnCotizacionTest {

    private static final Vehiculo SPARK = Vehiculo.builder().id("v1").marca("Chevrolet").modelo("Spark").anio(2018).build();

    private static PiezaTaller pieza(int stock, Double precio) {
        return PiezaTaller.builder().id("t1|REF:BP1").tallerId("t1").clave("REF:BP1").nombre("Pastillas").marca("Bosch").referencia("BP1")
                .compatibilidadUniversal(true).stock(stock).precioVenta(precio)
                .lotes(new ArrayList<>(List.of(PiezaTaller.Lote.builder().id("l1").cantidad(stock).disponible(stock)
                        .costoUnitario(99_999.0).build()))).build();
    }

    private static Producto producto() {
        Producto p = new Producto();
        p.setId("p1");
        p.setNombre("Pastillas");
        p.setReferencia("BP1");
        p.setTipo("STOCK");
        p.setStockActual(10);
        p.setPrecioVenta(90_000.0);
        p.setTiendaId("s1");
        p.setCompatibilidadUniversal(true);
        return p;
    }

    @Test
    void con_menos_de_lo_que_se_necesita_se_puede_elegir_y_dice_cuanto_falta() {
        OfertaRepuestoDTO o = BuscadorRepuestosService.evaluarPieza(pieza(2, 135_000.0), 5, SPARK);

        assertTrue(o.isSeleccionable());
        assertEquals(OfertaRepuestoDTO.PARCIAL, o.getDisponibilidad());
        assertEquals("Tienes 2 de 5: pide las otras 3 a una tienda", o.getMotivo());
        assertEquals(CotizacionItem.ORIGEN_TALLER, o.getOrigen());
        assertEquals("t1|REF:BP1", o.getPiezaTallerId());
        assertNull(o.getProductoId());
        assertNull(o.getTiendaId());
    }

    @Test
    void sin_precio_no_se_puede_cotizar() {
        OfertaRepuestoDTO o = BuscadorRepuestosService.evaluarPieza(pieza(2, null), 1, SPARK);
        assertFalse(o.isSeleccionable());
        assertEquals("Ponle precio de venta en Mi inventario", o.getMotivo());
    }

    @Test
    void lo_incompatible_tampoco_aunque_sea_del_taller() {
        PiezaTaller p = pieza(2, 135_000.0);
        p.setCompatibilidadUniversal(false);
        p.setModelosCompatibles(List.of(new com.drivemaster.drivemaster.model.Compatibilidad("Renault", "Logan", 2010, 2020)));
        assertFalse(BuscadorRepuestosService.evaluarPieza(p, 1, SPARK).isSeleccionable());
    }

    @Test
    void tu_inventario_va_antes_que_las_tiendas_aunque_la_tienda_sea_mas_barata() {
        PiezaTaller propia = pieza(1, 135_000.0);
        Producto dePropia = producto();
        OfertaRepuestoDTO oferta = BuscadorRepuestosService.evaluarPieza(propia, 1, SPARK);
        OfertaRepuestoDTO deTienda = BuscadorRepuestosService.evaluar(producto(), Tienda.builder().id("s1").nombre("Sur").build(), 1, null, SPARK);

        List<RepuestoBuscadoDTO> grupos = BuscadorRepuestosService.agrupar(List.of(
                new BuscadorRepuestosService.Oferta(producto(), deTienda), new BuscadorRepuestosService.Oferta(dePropia, oferta)));

        assertEquals(1, grupos.size(), "Misma referencia: misma pieza");
        assertEquals(CotizacionItem.ORIGEN_TALLER, grupos.get(0).getOfertas().get(0).getOrigen());
        assertEquals(10, grupos.get(0).getStockTotal(), "El resumen de tiendas no cuenta el inventario del taller");
        assertEquals(1, grupos.get(0).getTiendasConStock());
    }

    // ─── IVA ─────────────────────────────────────────────────────────────────

    private static CotizacionItem deTaller(double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).origen(CotizacionItem.ORIGEN_TALLER).cantidad(1).precioUnitario(precio).build();
    }

    private static CotizacionItem deTienda(double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).tiendaId("s1").cantidad(1).precioUnitario(precio).build();
    }

    private static CotizacionItem mano(double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).cantidad(1).precioUnitario(precio).build();
    }

    @Test
    void responsable_las_piezas_del_taller_llevan_19() {
        var t = CalculadoraCotizacion.calcular(List.of(deTaller(100_000), deTienda(50_000), mano(10_000)), 19, 19, 0, 10);
        assertEquals(50_000, t.subtotalRepuestos());
        assertEquals(100_000, t.subtotalRepuestosTaller());
        assertEquals(19_000, t.ivaRepuestosTaller());
        assertEquals(9_500, t.ivaRepuestos());
        assertEquals(160_000 + 30_400, t.total());
    }

    @Test
    void no_responsable_sus_piezas_y_su_mano_de_obra_van_sin_iva_y_la_tienda_con_el_suyo() {
        var t = CalculadoraCotizacion.calcular(List.of(deTaller(100_000), deTienda(50_000), mano(10_000)), 19, 0, 0, 10);
        assertEquals(0, t.ivaRepuestosTaller());
        assertEquals(0, t.ivaManoObra());
        assertEquals(9_500, t.ivaRepuestos());
        assertEquals(169_500, t.total());
    }

    @Test
    void el_anticipo_sigue_topado_en_la_mano_de_obra() {
        var t = CalculadoraCotizacion.calcular(List.of(deTaller(100_000)), 19, 19, 50_000, 10);
        assertEquals(0, t.anticipo());
        assertEquals(119_000, t.totalAPagar());
    }

    @Test
    void con_regimenes_distintos_el_recibo_dice_de_quien_es_cada_iva() {
        Cotizacion c = Cotizacion.builder().ivaRepuestosPct(19.0).ivaManoObraPct(0.0).subtotalRepuestos(50_000.0).ivaRepuestos(9_500.0)
                .subtotalRepuestosTaller(100_000.0).ivaRepuestosTaller(0.0).subtotalManoObra(10_000.0).ivaManoObra(0.0).build();
        assertEquals(List.of(new CalculadoraCotizacion.LineaIva("IVA repuestos de tiendas (19 %)", 9_500),
                new CalculadoraCotizacion.LineaIva("IVA del taller (0 %)", 0)), CalculadoraCotizacion.lineasIva(c));
    }

    // ─── Riesgo 2: privacidad, sobre el JSON ─────────────────────────────────

    private static final List<String> PROHIBIDAS = List.of("costo", "margen", "ganancia", "recargo", "lote", "99999");

    private static void sinDatosDelTaller(Object respuesta) throws Exception {
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(respuesta).toLowerCase(Locale.ROOT);
        for (String palabra : PROHIBIDAS) assertFalse(json.contains(palabra), "El JSON trae \"" + palabra + "\": " + json);
    }

    private static Cotizacion cotizacionConPieza() {
        OfertaRepuestoDTO o = BuscadorRepuestosService.evaluarPieza(pieza(2, 135_000.0), 1, SPARK);
        return Cotizacion.builder().id("c1").ordenId("o1").estado(Cotizacion.EMITIDA).version(1).vigenciaHasta(LocalDateTime.now().plusDays(1))
                .items(List.of(CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).origen(o.getOrigen()).piezaTallerId(o.getPiezaTallerId())
                        .descripcion("Pastillas").productoNombre(o.getNombre()).cantidad(1).precioUnitario(o.getPrecio()).subtotal(o.getPrecio()).build()))
                .subtotalRepuestosTaller(135_000.0).ivaRepuestosTaller(25_650.0).comisionInformativa(13_500.0).build();
    }

    @Test
    void riesgo2_la_cotizacion_del_cliente_y_del_enlace_publico_no_traen_costo_ni_margen() throws Exception {
        sinDatosDelTaller(CotizacionDTO.completa(cotizacionConPieza()));
        sinDatosDelTaller(CotizacionDTO.publica(cotizacionConPieza()));
    }

    @Test
    void riesgo2_la_etiqueta_del_origen_si_le_llega_al_cliente_y_al_enlace_publico() {
        assertEquals(CotizacionItem.ORIGEN_TALLER, CotizacionDTO.publica(cotizacionConPieza()).getItems().get(0).getOrigen());
        assertEquals(CotizacionItem.ORIGEN_TALLER, CotizacionDTO.completa(cotizacionConPieza()).getItems().get(0).getOrigen());
    }

    @Test
    void riesgo2_la_oferta_y_el_repuesto_elegido_tampoco() throws Exception {
        sinDatosDelTaller(BuscadorRepuestosService.evaluarPieza(pieza(2, 135_000.0), 1, SPARK));
        sinDatosDelTaller(RepuestoSeleccionado.builder().origen("TALLER").piezaTallerId("t1|REF:BP1").productoNombre("Pastillas").precioReferencia(135_000.0).build());
    }

    @Test
    void riesgo2_ninguna_clase_que_ve_el_cliente_tiene_campos_de_costo() {
        Stream.of(CotizacionItem.class, RepuestoSeleccionado.class, OfertaRepuestoDTO.class, CotizacionDTO.class, Cotizacion.class)
                .flatMap(c -> Stream.of(c.getDeclaredFields()))
                .map(f -> f.getName().toLowerCase(Locale.ROOT))
                .forEach(nombre -> assertTrue(Stream.of("costo", "margen", "ganancia").noneMatch(nombre::contains), nombre));
    }

    // ─── Tope por precio público ─────────────────────────────────────────────

    @Test
    void el_tope_compara_como_paga_el_cliente_con_el_iva_de_cada_uno() {
        assertEquals(100_000, PrecioPublicoTaller.topeDe(100_000, 19, 19), "Responsable: el mismo público");
        assertEquals(119_000, PrecioPublicoTaller.topeDe(100_000, 19, 0), "No responsable: el público con IVA, porque no le suma IVA");
        assertEquals(33_613, PrecioPublicoTaller.topeDe(33_613, 19, 19));
    }

    @Test
    void si_el_precio_del_taller_quedo_sobre_el_publico_se_cotiza_al_publico_y_se_dice() {
        OfertaRepuestoDTO o = BuscadorRepuestosService.evaluarPieza(pieza(3, 135_000.0), 1, SPARK, 120_000.0);
        assertEquals(120_000, o.getPrecio());
        assertTrue(o.isPrecioTopado());
        assertTrue(o.getMotivo().startsWith("Se cotiza a $120.000"), o.getMotivo());

        OfertaRepuestoDTO bajo = BuscadorRepuestosService.evaluarPieza(pieza(3, 100_000.0), 1, SPARK, 120_000.0);
        assertEquals(100_000, bajo.getPrecio(), "Si está por debajo, se respeta el del taller");
        assertFalse(bajo.isPrecioTopado());
    }

    @Test
    void un_taller_aprobado_ve_el_precio_con_descuento_de_cada_tienda_y_el_cliente_no() {
        var dto = new com.drivemaster.drivemaster.dto.catalogo.ProductoCatalogoDTO("p1", "Pastillas", null, null, null, null, 100_000.0,
                "STOCK", true, 3, null, null, null, null, null, null, null);
        var paraTaller = dto.paraTaller(25);
        assertEquals(100_000, paraTaller.precio(), "El público sigue a la vista, tachado");
        assertEquals(75_000, paraTaller.precioTaller());
        assertEquals(25.0, paraTaller.descuentoTallerPct());
        assertNull(dto.precioTaller(), "Un cliente no ve precio de taller");
    }
}
