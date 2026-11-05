package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.plataforma.DetallePlataformaDTO.Pedido;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;

class PlataformaDetalleTest {

    private static final LocalDateTime PAGO = LocalDateTime.of(2026, 10, 20, 9, 0);

    private MongoTemplate mongo;
    private PagoRepository pagos;
    private RepartoPagoRepository repartos;
    private PlataformaDetalleService servicio;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        pagos = mock(PagoRepository.class);
        repartos = mock(RepartoPagoRepository.class);
        ReembolsoRepository reembolsos = mock(ReembolsoRepository.class);
        when(reembolsos.findByPagoId(any())).thenReturn(List.of());
        servicio = new PlataformaDetalleService(mongo, pagos, repartos, reembolsos);
    }

    private static PedidoTienda pedido() {
        CotizacionItem item = new CotizacionItem();
        item.setProductoNombre("Pastillas de freno");
        item.setCantidad(2);
        item.setPrecioUnitario(50000.0);
        item.setSubtotal(100000.0);
        return PedidoTienda.builder().id("p1").origen(PedidoTienda.ORIGEN_CATALOGO).estado(PedidoTienda.ENTREGADO)
                .entrega(PedidoTienda.ENVIO).tiendaId("t1").tiendaNombre("Frenos Sur").clienteId("c1").clienteNombre("Ana")
                .pagoId(7L).items(List.of(item)).subtotal(100000.0).iva(19000.0).total(119000.0)
                .envioReceptor("Ana").envioDireccion("Cra 10").envioCiudad("Cartagena").costoEnvio(0.0)
                .fechaCreacion(PAGO).fechaPreparacion(PAGO.plusHours(2)).fechaDespacho(PAGO.plusDays(1))
                .fechaRecibidoCliente(PAGO.plusDays(3)).build();
    }

    private static RepartoPago linea(String tipo, String pedidoId, double monto) {
        RepartoPago r = new RepartoPago();
        r.setPagoId(7L);
        r.setBeneficiarioTipo(tipo);
        r.setBeneficiarioNombre(tipo);
        r.setPedidoTiendaId(pedidoId);
        r.setMonto(monto);
        r.setEstado(RepartoPago.LIQUIDADO);
        return r;
    }

    @Test
    void elDetalleTraeLaLineaDeTiempoEnOrdenElPagoYSoloSuParteDelReparto() {
        when(mongo.findById("p1", PedidoTienda.class)).thenReturn(pedido());
        when(mongo.findById("c1", Usuario.class)).thenReturn(Usuario.builder().id("c1").nombre("Ana Pérez")
                .correo("ana.perez@d.co").ciudad("Cartagena").build());
        when(mongo.count(any(Query.class), eq(PedidoTienda.class))).thenReturn(2L);
        PagoEntity pago = new PagoEntity();
        pago.setId(7L);
        pago.setReferencia("DM-7");
        pago.setTransactionId("wompi-123");
        pago.setEstado("PAGADO");
        pago.setMonto(238000.0);
        when(pagos.findById(7L)).thenReturn(Optional.of(pago));
        // El pago cubre dos pedidos: la línea de la otra tienda no es de este pedido; la comisión es de todo el pago
        when(repartos.findByPagoId(7L)).thenReturn(List.of(linea("TIENDA", "p1", 90000), linea("TIENDA", "p2", 90000),
                linea("PLATAFORMA", null, 17241)));

        Pedido d = servicio.pedido("p1", PAGO.plusDays(5));

        assertEquals(List.of("Pagado", "En preparación", "Despachado", "Recibido por el cliente"),
                d.lineaDeTiempo().stream().map(e -> e.etiqueta()).toList());
        assertEquals("wompi-123", d.pago().transaccion());
        assertEquals(2, d.pago().pedidosDelPago());
        assertEquals(2, d.reparto().size());
        assertTrue(d.reparto().stream().anyMatch(r -> "PLATAFORMA".equals(r.beneficiarioTipo()) && r.delPago()));
        assertTrue(d.reparto().stream().noneMatch(r -> r.monto() == 90000 && r.delPago()));
        assertEquals("an***@d.co", d.cliente().correo(), "el correo va enmascarado, como en las listas");
        assertEquals("ENVIO", d.entrega().tipo());
        assertEquals(1, d.lineas().size());
        assertFalse(d.toString().contains("ana.perez@d.co"));
    }

    @Test
    void unPedidoQueNoExisteEs404() {
        assertThrows(ResponseStatusException.class, () -> servicio.pedido("nada", PAGO));
    }

    @Test
    void elDetalleDeClienteSoloEsDeClientes() {
        when(mongo.findById("u9", Usuario.class)).thenReturn(Usuario.builder().id("u9").rol("TIENDA").build());
        assertThrows(ResponseStatusException.class, () -> servicio.cliente("u9"));
        assertThrows(ResponseStatusException.class, () -> servicio.negocio("OTRO", "x"));
    }
}
