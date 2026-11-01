package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO;
import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Desglose;
import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Linea;
import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Resumen;
import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO.ComisionRetenida;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lo que ve cada negocio de su dinero, siempre lo suyo, y cómo se separa la comisión retenida para el ADMIN. */
class DesgloseLiquidacionServiceTest {

    private static final LocalDateTime PAGADA = LocalDateTime.of(2026, 10, 20, 9, 0);

    private RepartoPagoRepository repartos;
    private ParametrosMarketplace parametros;
    private DesgloseLiquidacionService servicio;

    @BeforeEach
    void setUp() {
        repartos = mock(RepartoPagoRepository.class);
        parametros = mock(ParametrosMarketplace.class);
        PedidoTiendaRepository pedidos = mock(PedidoTiendaRepository.class);
        OrdenServicioRepository ordenes = mock(OrdenServicioRepository.class);
        TiendaRepository tiendas = mock(TiendaRepository.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        TallerService talleres = mock(TallerService.class);

        when(usuarios.obtenerPorCorreo("tienda@demo.com")).thenReturn(Usuario.builder().id("u1").build());
        when(tiendas.findByUsuarioId("u1")).thenReturn(Optional.of(Tienda.builder().id("s1").build()));
        when(pedidos.findById("ped-1")).thenReturn(Optional.of(PedidoTienda.builder().id("ped-1").tiendaId("s1").build()));
        when(pedidos.findById("ped-ajeno")).thenReturn(Optional.of(PedidoTienda.builder().id("ped-ajeno").tiendaId("s2").build()));
        when(talleres.obtenerPorUsuario("taller@demo.com")).thenReturn(Taller.builder().id("t1").build());
        when(ordenes.findById("o1")).thenReturn(Optional.of(OrdenServicio.builder().id("o1").tallerId("t1").build()));
        when(ordenes.findById("o-ajena")).thenReturn(Optional.of(OrdenServicio.builder().id("o-ajena").tallerId("t2").build()));

        servicio = new DesgloseLiquidacionService(repartos, pedidos, ordenes, tiendas, usuarios, talleres, parametros);
    }

    private static RepartoPago conRetencion(String estado) {
        return RepartoPago.builder().beneficiarioTipo(RepartoPago.TIENDA).beneficiarioId("s1").concepto("CATALOGO").estado(estado)
                .montoBruto(238_000.0).comisionBase(200_000.0).comisionPct(10.0).comisionCalculada(20_000.0)
                .comisionRetenida(20_000.0).monto(218_000.0).nota("Interna").motivoFallo("Interno").build();
    }

    @Test
    void laTiendaVeLaVentaLaComisionYLoQueRecibe() {
        RepartoPago r = conRetencion(RepartoPago.LIQUIDADO);
        r.setFechaLiquidacion(PAGADA);
        r.setModoLiquidacion("WOMPI");
        r.setReferenciaLiquidacion("WOMPI-12-ab34 [ID:pay-9]");
        when(repartos.findByPedidoTiendaId("ped-1")).thenReturn(List.of(r));
        when(parametros.comisionRetenida()).thenReturn(true);

        Desglose d = servicio.dePedido("tienda@demo.com", "ped-1");

        Linea l = d.lineas().get(0);
        assertTrue(d.comisionSeDescuenta());
        assertEquals(238_000, l.venta());
        assertEquals(200_000, l.comisionBase());
        assertEquals(20_000, l.comision());
        assertEquals(DesgloseLiquidacionDTO.DESCONTADA, l.estadoComision());
        assertEquals(218_000, l.recibes());
        assertEquals(DesgloseLiquidacionDTO.PAGADO, l.estado());
        assertEquals(PAGADA, l.fechaPago());
        assertEquals("WOMPI-12-ab34", l.referencia(), "sin el id interno de Wompi");
    }

    @Test
    void laTiendaNoVeUnPedidoAjeno() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.dePedido("tienda@demo.com", "ped-ajeno"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void elTallerVeSusDosLineasDeLaOrdenEnOrden() {
        RepartoPago cotizacion = RepartoPago.builder().beneficiarioTipo(RepartoPago.TALLER).beneficiarioId("t1").concepto("COTIZACION")
                .estado(RepartoPago.PENDIENTE).monto(54_500.0).montoBruto(59_500.0).comisionRetenida(5_000.0)
                .fechaCreacion(PAGADA.plusDays(3)).build();
        RepartoPago diagnostico = RepartoPago.builder().beneficiarioTipo(RepartoPago.TALLER).beneficiarioId("t1").concepto("DIAGNOSTICO")
                .estado(RepartoPago.POR_LIQUIDAR).monto(54_500.0).montoBruto(59_500.0).comisionRetenida(5_000.0).fechaCreacion(PAGADA).build();
        when(repartos.findByOrdenServicioIdAndBeneficiarioTipo("o1", RepartoPago.TALLER)).thenReturn(List.of(cotizacion, diagnostico));

        List<Linea> lineas = servicio.deOrden("taller@demo.com", "o1").lineas();

        assertEquals(List.of("DIAGNOSTICO", "COTIZACION"), lineas.stream().map(Linea::concepto).toList());
        assertEquals(DesgloseLiquidacionDTO.LISTO, lineas.get(0).estado());
        assertEquals(DesgloseLiquidacionDTO.EN_ESPERA, lineas.get(1).estado());
    }

    @Test
    void elTallerNoVeUnaOrdenAjena() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.deOrden("taller@demo.com", "o-ajena"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void conLaBanderaApagadaLaComisionSeMuestraPeroNoSeDescuenta() {
        RepartoPago r = RepartoPago.builder().estado(RepartoPago.PENDIENTE).monto(238_000.0).montoBruto(238_000.0)
                .comisionPct(10.0).comisionCalculada(20_000.0).comisionRetenida(0.0).build();

        Linea l = DesgloseLiquidacionService.linea(r, false);

        assertEquals(DesgloseLiquidacionDTO.TODAVIA_NO, l.estadoComision());
        assertEquals(20_000, l.comision());
        assertEquals(238_000, l.recibes());
    }

    @Test
    void unaLineaAnteriorNoInventaComisionYDiceQueNoSeDescontoSiYaSeCobra() {
        RepartoPago anterior = RepartoPago.builder().estado(RepartoPago.LIQUIDADO).monto(238_000.0).build();

        Linea l = DesgloseLiquidacionService.linea(anterior, true);

        assertNull(l.comision());
        assertEquals(DesgloseLiquidacionDTO.NO_EN_ESTE_PAGO, l.estadoComision());
        assertEquals(238_000, l.venta());
        assertEquals(238_000, l.recibes());
        assertEquals(DesgloseLiquidacionDTO.TODAVIA_NO, DesgloseLiquidacionService.linea(anterior, false).estadoComision());
    }

    @Test
    void cadaEstadoDeLaLineaSeTraduceParaElNegocio() {
        assertEquals(DesgloseLiquidacionDTO.EN_ESPERA, DesgloseLiquidacionService.estado(conRetencion(RepartoPago.PENDIENTE)));
        for (String listo : List.of(RepartoPago.POR_LIQUIDAR, RepartoPago.PROCESANDO, RepartoPago.FALLIDO)) {
            assertEquals(DesgloseLiquidacionDTO.LISTO, DesgloseLiquidacionService.estado(conRetencion(listo)), listo);
        }
        assertEquals(DesgloseLiquidacionDTO.ANULADO, DesgloseLiquidacionService.estado(conRetencion(RepartoPago.ANULADO)));
        assertEquals(DesgloseLiquidacionDTO.PAGADO, DesgloseLiquidacionService.estado(conRetencion(RepartoPago.LIQUIDADO)));
    }

    @Test
    void loSimuladoNoSeMuestraComoPagado() {
        RepartoPago r = conRetencion(RepartoPago.LIQUIDADO);
        r.setModoLiquidacion("SIMULADO");

        assertEquals(DesgloseLiquidacionDTO.SIMULADO, DesgloseLiquidacionService.estado(r));
    }

    @Test
    void sinPagoTerminadoNoHayFechaNiReferencia() {
        RepartoPago enCurso = conRetencion(RepartoPago.PROCESANDO);
        enCurso.setReferenciaLiquidacion("WOMPI-12-ab34");
        RepartoPago sinTransferencia = conRetencion(RepartoPago.LIQUIDADO);
        sinTransferencia.setModoLiquidacion(RepartoPago.SIN_TRANSFERENCIA);
        sinTransferencia.setReferenciaLiquidacion("SIN-TRANSFERENCIA");

        assertNull(DesgloseLiquidacionService.linea(enCurso, true).referencia());
        assertNull(DesgloseLiquidacionService.linea(enCurso, true).fechaPago());
        assertNull(DesgloseLiquidacionService.linea(sinTransferencia, true).referencia());
    }

    @Test
    void elResumenSinFilasEsCeroYConservaLaBandera() {
        assertEquals(new Resumen(0, 0, 0, 0, true), Resumen.de(List.of(), true));
    }

    @Test
    void elResumenSeparaLaComisionCalculadaDeLaDescontada() {
        // [venta, descontada, calculada, recibe]
        List<Object[]> filas = java.util.Collections.singletonList(new Object[]{238_000.0, 0.0, 20_000.0, 238_000.0});

        assertEquals(new Resumen(238_000, 20_000, 0, 238_000, false), Resumen.de(filas, false));
    }

    @Test
    void laComisionCobradaEsSoloLaDeLineasPagadasDeVerdad() {
        ComisionRetenida c = ComisionRetenida.de(Arrays.asList(
                new Object[]{"LIQUIDADO", "WOMPI", "ID", 20_000.0},
                new Object[]{"LIQUIDADO", "WOMPI", "", 6_000.0},
                new Object[]{"LIQUIDADO", "MANUAL", "", 5_000.0},
                new Object[]{"LIQUIDADO", "SIMULADO", "", 7_000.0},
                new Object[]{"PENDIENTE", "", "", 3_000.0},
                new Object[]{"FALLIDO", "WOMPI", "ID", 2_000.0},
                new Object[]{"ANULADO", "", "", 4_000.0}));

        assertEquals(new ComisionRetenida(25_000, 7_000, 5_000, 4_000, 6_000), c, "sin id de Wompi no cuenta y lo SIM- nunca");
    }
}
