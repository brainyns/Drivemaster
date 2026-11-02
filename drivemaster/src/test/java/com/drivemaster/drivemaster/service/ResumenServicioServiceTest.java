package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Admin;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Cliente;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Fila;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Taller;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumenServicioServiceTest {

    private RepartoPagoRepository repartos;
    private PagoRepository pagos;
    private ReembolsoRepository reembolsos;
    private OrdenServicioRepository ordenes;
    private CotizacionRepository cotizaciones;
    private UsuarioService usuarios;
    private TallerService talleres;
    private ResumenServicioService service;

    @BeforeEach
    void setUp() {
        repartos = mock(RepartoPagoRepository.class);
        pagos = mock(PagoRepository.class);
        reembolsos = mock(ReembolsoRepository.class);
        ordenes = mock(OrdenServicioRepository.class);
        cotizaciones = mock(CotizacionRepository.class);
        usuarios = mock(UsuarioService.class);
        talleres = mock(TallerService.class);
        ParametrosMarketplace parametros = mock(ParametrosMarketplace.class);
        when(parametros.comisionRetenida()).thenReturn(true);
        service = new ResumenServicioService(repartos, pagos, reembolsos, ordenes, cotizaciones, usuarios, talleres, parametros);
    }

    private static RepartoPago linea(String concepto, String estado, double bruto, double base, double comision) {
        return RepartoPago.builder().concepto(concepto).beneficiarioTipo(RepartoPago.TALLER).beneficiarioId("t1").estado(estado)
                .montoBruto(bruto).monto(bruto - comision).comisionBase(base).comisionCalculada(comision).comisionRetenida(comision).build();
    }

    private static RepartoPago diagnostico(String estado) {
        return linea("DIAGNOSTICO", estado, 50_000, 42_017, 4_202);
    }

    private static RepartoPago cotizacion(String estado) {
        return linea("COTIZACION", estado, 69_000, 57_983, 5_798);
    }

    private static OrdenServicio orden() {
        return OrdenServicio.builder().id("o1").usuarioId("u1").tallerId("t1").cotizacionId("c1").pagoCotizacionId(20L).build();
    }

    private static Cotizacion aprobada(double anticipo, double totalAPagar) {
        return Cotizacion.builder().id("c1").estado(Cotizacion.APROBADA).anticipoDiagnostico(anticipo).totalAPagar(totalAPagar).build();
    }

    private static Taller calcular(OrdenServicio o, Cotizacion c, RepartoPago... lineas) {
        return ResumenServicioService.calcular(o, c, Arrays.asList(lineas), true);
    }

    @Test
    void elTotalUneDiagnosticoYCotizacionSinContarDosVecesElAnticipo() {
        Taller t = calcular(orden(), aprobada(50_000, 307_000), diagnostico(RepartoPago.POR_LIQUIDAR), cotizacion(RepartoPago.POR_LIQUIDAR));

        assertEquals(50_000, t.diagnosticoPagado());
        assertEquals(119_000, t.manoDeObra());
        assertEquals(50_000, t.anticipo());
        assertEquals(69_000, t.cotizacionPagada());
        assertEquals(119_000, t.totalGenerado());
        assertEquals(t.totalGenerado(), t.diagnosticoPagado() + t.manoDeObra() - t.anticipo(), "el recibo cuadra");
        assertEquals(10_000, t.comision());
        assertEquals(109_000, t.recibe());
        assertEquals(DesgloseLiquidacionDTO.LISTO, t.estado());
        assertEquals(2, t.lineas().size());
    }

    @Test
    void elTotalDiceCuantoEsIva() {
        assertEquals(19_000, calcular(orden(), aprobada(50_000, 307_000), diagnostico(RepartoPago.PENDIENTE), cotizacion(RepartoPago.PENDIENTE)).iva());
    }

    @Test
    void conLineasAnterioresAlDesgloseNoSeInventaElIva() {
        RepartoPago antigua = RepartoPago.builder().concepto("DIAGNOSTICO").estado(RepartoPago.PENDIENTE).monto(50_000.0).build();

        Taller t = calcular(orden(), null, antigua);

        assertNull(t.iva());
        assertEquals(50_000, t.totalGenerado(), "sin bruto guardado, el monto es el bruto");
        assertEquals(0, t.comision());
    }

    @Test
    void conSoloElDiagnosticoNoAparecenManoDeObraNiAnticipo() {
        OrdenServicio sinPagar = orden();
        sinPagar.setPagoCotizacionId(null);

        Taller t = calcular(sinPagar, aprobada(50_000, 307_000), diagnostico(RepartoPago.PENDIENTE));

        assertEquals(0, t.manoDeObra());
        assertEquals(0, t.anticipo());
        assertEquals(50_000, t.totalGenerado());
    }

    @Test
    void unaCotizacionRechazadaDejaSoloElDiagnostico() {
        Cotizacion rechazada = aprobada(50_000, 307_000);
        rechazada.setEstado(Cotizacion.RECHAZADA);

        Taller t = calcular(orden(), rechazada, diagnostico(RepartoPago.POR_LIQUIDAR));

        assertEquals(0, t.anticipo());
        assertEquals(50_000, t.totalGenerado());
    }

    @Test
    void siElDiagnosticoCubrioLaCotizacionNoHayCotizacionPagadaYElTotalEsElDiagnostico() {
        OrdenServicio sinPago = orden();
        sinPago.setPagoCotizacionId(null);
        Cotizacion sinSaldo = aprobada(30_000, 0);

        Taller t = calcular(sinPago, sinSaldo, diagnostico(RepartoPago.PENDIENTE));

        assertEquals(30_000, t.manoDeObra());
        assertEquals(30_000, t.anticipo());
        assertEquals(0, t.cotizacionPagada());
        assertEquals(50_000, t.totalGenerado());
    }

    @Test
    void conRepuestosYSinSaldoDelTallerLaManoDeObraSeCubreConElAnticipo() {
        Cotizacion conRepuestos = aprobada(50_000, 238_000);
        conRepuestos.setItems(List.of(CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).build()));

        Taller t = calcular(orden(), conRepuestos, diagnostico(RepartoPago.PENDIENTE));

        assertEquals(50_000, t.manoDeObra());
        assertEquals(50_000, t.totalGenerado());
    }

    @Test
    void lasLineasAnuladasNoSumanPeroSiguenEnElDetalle() {
        Taller t = calcular(orden(), null, diagnostico(RepartoPago.ANULADO), cotizacion(RepartoPago.POR_LIQUIDAR));

        assertEquals(69_000, t.totalGenerado());
        assertEquals(5_798, t.comision());
        assertEquals(2, t.lineas().size());
    }

    @Test
    void elEstadoDeLaOrdenResumeElDeSusLineas() {
        OrdenServicio o = orden();
        assertEquals(ResumenServicioDTO.SIN_PAGOS, calcular(o, null).estado());
        assertEquals(ResumenServicioDTO.SIN_PAGOS, calcular(o, null, diagnostico(RepartoPago.ANULADO)).estado());
        assertEquals(DesgloseLiquidacionDTO.EN_ESPERA, calcular(o, null, diagnostico(RepartoPago.PENDIENTE), cotizacion(RepartoPago.PENDIENTE)).estado());
        assertEquals(DesgloseLiquidacionDTO.LISTO, calcular(o, null, diagnostico(RepartoPago.POR_LIQUIDAR), cotizacion(RepartoPago.PENDIENTE)).estado());
        assertEquals(ResumenServicioDTO.PARCIAL, calcular(o, null, diagnostico(RepartoPago.LIQUIDADO), cotizacion(RepartoPago.POR_LIQUIDAR)).estado());
        assertEquals(DesgloseLiquidacionDTO.PAGADO, calcular(o, null, diagnostico(RepartoPago.LIQUIDADO), cotizacion(RepartoPago.LIQUIDADO)).estado());
    }

    @Test
    void laFilaDeLiquidacionesSumaLasDosLineasDeLaOrden() {
        Fila f = ResumenServicioService.fila(List.of(diagnostico(RepartoPago.LIQUIDADO), cotizacion(RepartoPago.POR_LIQUIDAR), cotizacion(RepartoPago.ANULADO)));

        assertEquals(119_000, f.totalGenerado());
        assertEquals(10_000, f.comision());
        assertEquals(109_000, f.recibe());
        assertEquals(2, f.lineas());
        assertEquals(ResumenServicioDTO.PARCIAL, f.estado());
    }

    // ─── Quién ve cada resumen ───────────────────────────────────────────────

    private void tallerConOrden(String tallerId) {
        when(talleres.obtenerPorUsuario("taller@demo.com")).thenReturn(com.drivemaster.drivemaster.model.Taller.builder().id(tallerId).build());
        when(ordenes.findById("o1")).thenReturn(Optional.of(orden()));
    }

    @Test
    void elTallerSoloVeSusLineasDeLaOrden() {
        tallerConOrden("t1");
        RepartoPago deOtro = diagnostico(RepartoPago.POR_LIQUIDAR);
        deOtro.setBeneficiarioId("t9");
        when(repartos.findByOrdenServicioIdAndBeneficiarioTipo("o1", RepartoPago.TALLER)).thenReturn(List.of(deOtro, cotizacion(RepartoPago.POR_LIQUIDAR)));

        assertEquals(69_000, service.deTaller("taller@demo.com", "o1").totalGenerado());
    }

    @Test
    void otroTallerRecibe404() {
        tallerConOrden("t9");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.deTaller("taller@demo.com", "o1"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void otroClienteRecibe404() {
        when(usuarios.obtenerPorCorreo("otra@demo.com")).thenReturn(Usuario.builder().id("u9").build());
        when(ordenes.findById("o1")).thenReturn(Optional.of(orden()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.deCliente("otra@demo.com", "o1"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    private static PagoEntity pago(String concepto, String estado, double monto) {
        PagoEntity p = new PagoEntity();
        p.setConcepto(concepto);
        p.setEstado(estado);
        p.setMonto(monto);
        return p;
    }

    private void pagosDeLaOrden() {
        when(ordenes.findById("o1")).thenReturn(Optional.of(orden()));
        when(pagos.findByOrdenServicioIdOrderByIdAsc("o1")).thenReturn(List.of(pago("DIAGNOSTICO", "PAGADO", 50_000),
                pago("COTIZACION", "PAGADO", 307_000), pago("COTIZACION", "PENDIENTE", 307_000)));
    }

    @Test
    void elClienteVeLoPagadoYNoCuentaUnPagoSinAprobar() {
        when(usuarios.obtenerPorCorreo("ana@demo.com")).thenReturn(Usuario.builder().id("u1").build());
        pagosDeLaOrden();

        Cliente c = service.deCliente("ana@demo.com", "o1");

        assertEquals(50_000, c.diagnostico());
        assertEquals(307_000, c.cotizacion());
        assertEquals(357_000, c.total());
    }

    @Test
    void elClienteVeSusDevolucionesAparteDeLoPagado() {
        when(usuarios.obtenerPorCorreo("ana@demo.com")).thenReturn(Usuario.builder().id("u1").build());
        pagosDeLaOrden();
        when(reembolsos.findByOrdenServicioId("o1")).thenReturn(List.of(
                Reembolso.builder().monto(100_000.0).estado(Reembolso.PENDIENTE).build(),
                Reembolso.builder().monto(38_000.0).estado(Reembolso.REALIZADO).build()));

        Cliente c = service.deCliente("ana@demo.com", "o1");

        assertEquals(357_000, c.total());
        assertEquals(100_000, c.devolucionPendiente());
        assertEquals(38_000, c.devuelto());
    }

    @Test
    void elAdminVeTallerClienteYRepuestosPorSeparado() {
        pagosDeLaOrden();
        when(repartos.findByOrdenServicioIdAndBeneficiarioTipo("o1", RepartoPago.TALLER)).thenReturn(List.of(diagnostico(RepartoPago.POR_LIQUIDAR)));
        RepartoPago tienda = linea("COTIZACION", RepartoPago.POR_LIQUIDAR, 238_000, 200_000, 20_000);
        RepartoPago anulada = linea("COTIZACION", RepartoPago.ANULADO, 99_000, 83_000, 8_300);
        when(repartos.findByOrdenServicioIdAndBeneficiarioTipo("o1", RepartoPago.TIENDA)).thenReturn(List.of(tienda, anulada));

        Admin a = service.deAdmin("o1");

        assertEquals(50_000, a.taller().totalGenerado(), "los repuestos no son del taller");
        assertEquals(238_000, a.repuestos());
        assertEquals(20_000, a.comisionRepuestos());
        assertEquals(357_000, a.cliente().total());
    }
}
