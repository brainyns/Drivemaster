package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** "En camino": lo comprado solo entra cuando el taller confirma que le llegó, y las cifras de cada pieza. */
class InventarioTallerServiceTest {

    private static final String CORREO = "taller@demo.com";
    private static final String PIEZA = PiezaTaller.idDe("t1", "REF:BP1");

    private final InventarioEnMemoria memoria = new InventarioEnMemoria();
    private TallerService tallerService;
    private PedidoTiendaRepository pedidos;
    private RecepcionEnvioService recepcion;
    private ParametrosMarketplace parametros;
    private InventarioTallerService service;
    private Taller taller;
    private Producto productoP1;

    @BeforeEach
    void setUp() {
        taller = Taller.builder().id("t1").nombre("AutoMax").estadoAprobacion("APROBADO").activo(true).responsableIva(true).build();
        tallerService = mock(TallerService.class);
        when(tallerService.obtenerPorUsuario(CORREO)).thenAnswer(i -> taller);
        pedidos = mock(PedidoTiendaRepository.class);
        recepcion = mock(RecepcionEnvioService.class);
        parametros = mock(ParametrosMarketplace.class);
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.ivaPct()).thenReturn(19.0);

        ProductoService productoService = mock(ProductoService.class);
        Producto producto = productoP1 = new Producto();
        producto.setId("p1");
        producto.setNombre("Pastillas");
        producto.setReferencia("BP1");
        producto.setCompatibilidadUniversal(true);
        producto.setPrecioVenta(150_000.0);
        when(productoService.obtenerPorId("p1")).thenReturn(producto);

        service = new InventarioTallerService(tallerService, memoria.piezaRepository, pedidos, productoService, memoria.stock,
                recepcion, mock(MongoTemplate.class), parametros, mock(com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository.class),
                new PrecioPublicoTaller(productoService, parametros));
    }

    private PedidoTienda pedido(String estado, String entrega) {
        PedidoTienda p = PedidoTienda.builder().id("ped1").compraId("com1").compradorTallerId("t1").tiendaId("s1").tiendaNombre("Repuestos Sur")
                .clienteId("u9").estado(estado).entrega(entrega).subtotal(500_000.0).iva(95_000.0)
                .items(List.of(CotizacionItem.builder().productoId("p1").productoNombre("Pastillas").referencia("BP1")
                        .cantidad(5).precioUnitario(100_000.0).build())).build();
        when(pedidos.findById("ped1")).thenReturn(Optional.of(p));
        when(pedidos.findByCompradorTallerIdAndFechaEntradaInventarioIsNullOrderByFechaCreacionDesc("t1")).thenReturn(List.of(p));
        return p;
    }

    private static void conflicto(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    private static void noEncontrado(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void lo_comprado_se_ve_en_camino_y_no_cuenta_como_stock_hasta_confirmar() {
        pedido(PedidoTienda.DESPACHADO, PedidoTienda.ENVIO);

        InventarioTallerDTO.Inventario antes = service.inventario(CORREO);
        assertTrue(antes.piezas().isEmpty());
        assertEquals(1, antes.enCamino().size());
        assertTrue(antes.enCamino().get(0).puedeConfirmar());
        assertEquals(0, memoria.stock.disponible("t1", PIEZA));

        service.confirmarLlegada(CORREO, "ped1");

        assertEquals(5, memoria.stock.disponible("t1", PIEZA));
        PiezaTaller.Lote lote = memoria.piezas.get(PIEZA).getLotes().get(0);
        assertEquals("com1", lote.getCompraId());
        assertEquals("Repuestos Sur", lote.getTiendaNombre());
        assertEquals(100_000, lote.getCostoUnitario());
        assertEquals(150_000, memoria.piezas.get(PIEZA).getPrecioVenta(), "Entra al precio público: el taller es responsable de IVA");
        assertTrue(memoria.piezas.get(PIEZA).getCompatibilidadUniversal());
    }

    @Test
    void en_un_envio_la_confirmacion_tambien_es_la_recepcion_que_paga_a_la_tienda() {
        pedido(PedidoTienda.DESPACHADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");
        verify(recepcion).confirmarRecepcion(CORREO, "ped1");
    }

    @Test
    void si_la_recepcion_se_confirmo_sola_las_piezas_igual_esperan_al_taller() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        assertEquals(0, memoria.stock.disponible("t1", PIEZA));
        service.confirmarLlegada(CORREO, "ped1");
        verify(recepcion, never()).confirmarRecepcion(anyString(), anyString());
        assertEquals(5, memoria.stock.disponible("t1", PIEZA));
    }

    @Test
    void un_retiro_se_confirma_despues_de_retirarlo_en_la_tienda() {
        pedido(PedidoTienda.LISTO_PARA_RETIRAR, PedidoTienda.RETIRO);
        conflicto(() -> service.confirmarLlegada(CORREO, "ped1"));

        pedido(PedidoTienda.RETIRADO, PedidoTienda.RETIRO);
        service.confirmarLlegada(CORREO, "ped1");
        assertEquals(5, memoria.stock.disponible("t1", PIEZA));
    }

    @Test
    void lo_que_la_tienda_no_ha_despachado_no_se_puede_confirmar() {
        pedido(PedidoTienda.PENDIENTE, PedidoTienda.ENVIO);
        assertFalse(service.inventario(CORREO).enCamino().get(0).puedeConfirmar());
        conflicto(() -> service.confirmarLlegada(CORREO, "ped1"));
        assertTrue(memoria.piezas.isEmpty());
    }

    @Test
    void con_un_problema_reportado_no_entra() {
        pedido(PedidoTienda.DESPACHADO, PedidoTienda.ENVIO).setProblemaReportado(true);
        conflicto(() -> service.confirmarLlegada(CORREO, "ped1"));
        assertTrue(memoria.piezas.isEmpty());
    }

    @Test
    void un_pedido_que_ya_no_va_a_llegar_no_se_muestra_en_camino() {
        pedido(PedidoTienda.NO_DESPACHADO, PedidoTienda.ENVIO);
        assertTrue(service.inventario(CORREO).enCamino().isEmpty());
    }

    @Test
    void confirmar_dos_veces_no_duplica() {
        PedidoTienda p = pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");
        p.setFechaEntradaInventario(java.time.LocalDateTime.now());
        conflicto(() -> service.confirmarLlegada(CORREO, "ped1"));
        assertEquals(5, memoria.stock.disponible("t1", PIEZA));
    }

    @Test
    void la_compra_de_un_cliente_nunca_entra_a_un_inventario() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO).setCompradorTallerId(null);
        noEncontrado(() -> service.confirmarLlegada(CORREO, "ped1"));
        assertTrue(memoria.piezas.isEmpty());
    }

    @Test
    void el_pedido_de_otro_taller_no_se_puede_confirmar() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO).setCompradorTallerId("t2");
        noEncontrado(() -> service.confirmarLlegada(CORREO, "ped1"));
    }

    @Test
    void un_taller_en_pausa_no_recibe() {
        taller.setActivo(false);
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.confirmarLlegada(CORREO, "ped1"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void el_no_responsable_de_iva_lleva_el_iva_a_su_costo() {
        taller.setResponsableIva(false);
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");
        PiezaTaller.Lote lote = memoria.piezas.get(PIEZA).getLotes().get(0);
        assertEquals(119_000, lote.getCostoUnitario());
        assertTrue(lote.getIvaEnCosto());
    }

    @Test
    void recargo_margen_y_ganancia_son_tres_cifras_distintas() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");

        InventarioTallerDTO.Pieza p = service.inventario(CORREO).piezas().get(0);

        assertEquals(150_000, p.precioVenta());
        assertEquals(100_000, p.costoUnitario());
        assertEquals(50.0, p.recargoPct());
        assertEquals(33.3, p.margenPct());
        assertEquals(15_000, p.comision());
        assertEquals(35_000, p.gananciaDespuesComision());
        assertFalse(p.enPerdida());
    }

    @Test
    void vender_en_perdida_se_avisa_pero_no_se_bloquea() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");

        InventarioTallerDTO.Pieza p = service.cambiarPrecio(CORREO, PIEZA, 105_000.0);

        assertEquals(105_000, p.precioVenta());
        assertEquals(-5_500, p.gananciaDespuesComision());
        assertTrue(p.enPerdida());
    }

    @Test
    void el_precio_debe_ser_mayor_que_cero_y_solo_lo_cambia_su_dueno() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");
        for (Double malo : new Double[]{null, 0.0, -1.0, 0.4}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.cambiarPrecio(CORREO, PIEZA, malo));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
        noEncontrado(() -> service.cambiarPrecio(CORREO, PiezaTaller.idDe("t2", "REF:BP1"), 10.0));
    }

    @Test
    void el_valor_del_inventario_suma_lo_disponible() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");
        InventarioTallerDTO.Inventario i = service.inventario(CORREO);
        assertEquals(500_000, i.valorACosto());
        assertEquals(750_000, i.valorAVenta());
    }

    // ─── Tope en el precio público ───────────────────────────────────────────

    private void enInventario() {
        pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        service.confirmarLlegada(CORREO, "ped1");
    }

    @Test
    void el_backend_no_deja_subir_el_precio_sobre_el_publico() {
        enInventario();
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.cambiarPrecio(CORREO, PIEZA, 150_001.0));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getReason().contains("no puede superar el precio público"), e.getReason());
        assertTrue(e.getReason().contains("$150.000"), e.getReason());
        assertEquals(150_000, service.cambiarPrecio(CORREO, PIEZA, 150_000.0).precioVenta());
        assertEquals(120_000, service.cambiarPrecio(CORREO, PIEZA, 120_000.0).precioVenta(), "Bajarlo sí se puede");
    }

    @Test
    void el_no_responsable_de_iva_puede_cobrar_hasta_lo_que_el_cliente_paga_con_iva_en_la_tienda() {
        taller.setResponsableIva(false);
        enInventario();
        InventarioTallerDTO.Pieza p = service.inventario(CORREO).piezas().get(0);
        assertEquals(178_500, p.tope(), "150.000 + 19 % = 178.500: el cliente paga lo mismo, sin IVA del taller");
        assertEquals(178_500, p.precioVenta(), "Entra con el tope como precio");
        assertEquals(178_500, p.precioPublicoFinal());
        assertThrows(ResponseStatusException.class, () -> service.cambiarPrecio(CORREO, PIEZA, 178_501.0));
    }

    @Test
    void si_el_publico_baja_se_cobra_el_publico_y_se_marca() {
        enInventario();
        service.cambiarPrecio(CORREO, PIEZA, 140_000.0);
        productoP1.setPrecioVenta(110_000.0);

        InventarioTallerDTO.Pieza p = service.inventario(CORREO).piezas().get(0);

        assertTrue(p.topado());
        assertEquals(140_000, p.precioVenta());
        assertEquals(110_000, p.precioCobrado());
        assertEquals(110_000 - 100_000 - 11_000, p.gananciaDespuesComision());
        assertTrue(p.perdidaConTope(), "Con el tope ya no cubre costo y comisión");
    }

    @Test
    void el_costo_queda_congelado_aunque_la_tienda_cambie_precio_o_descuento() {
        PedidoTienda p = pedido(PedidoTienda.ENTREGADO, PedidoTienda.ENVIO);
        p.getItems().get(0).setPrecioPublico(125_000.0);
        p.getItems().get(0).setDescuentoPct(20.0);
        // La tienda sube su precio después de la compra
        productoP1.setPrecioVenta(300_000.0);

        service.confirmarLlegada(CORREO, "ped1");

        PiezaTaller.Lote lote = memoria.piezas.get(PIEZA).getLotes().get(0);
        assertEquals(100_000, lote.getCostoUnitario());
        assertEquals(100_000, lote.getPrecioPagado());
        assertEquals(125_000, lote.getPrecioPublico());
        assertEquals(20.0, lote.getDescuentoPct());
    }
}
