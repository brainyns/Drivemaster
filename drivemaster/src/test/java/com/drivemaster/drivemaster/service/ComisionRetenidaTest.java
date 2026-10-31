package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.controller.ConfigController;
import com.drivemaster.drivemaster.model.mysql.Parametro;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.EstadoVentaRepository;
import com.drivemaster.drivemaster.repository.mysql.MetodoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ParametroRepository;
import com.drivemaster.drivemaster.service.dispersion.ManualDispersionService;
import com.drivemaster.drivemaster.service.dispersion.SimulatedDispersionService;
import com.drivemaster.drivemaster.service.dispersion.SolicitudDispersion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El cálculo de la comisión en cada línea, los límites del porcentaje y que a la dispersión llegue el neto. Los tres casos
 * completos (catálogo, diagnóstico y cotización) y los reembolsos están en las pruebas de sus servicios.
 */
class ComisionRetenidaTest {

    private static RepartoPago linea(double bruto) {
        return RepartoPago.builder().id(1L).beneficiarioTipo(RepartoPago.TIENDA).beneficiarioId("s1").monto(bruto).build();
    }

    private static RepartoPago conComision(double bruto, double base, double pct, boolean retener) {
        RepartoPago r = linea(bruto);
        ComisionPlataforma.aplicar(r, BigDecimal.valueOf(base), pct, retener);
        return r;
    }

    @Test
    void laComisionSaleDeLaBaseSinIvaYElIvaQuedaEntero() {
        RepartoPago r = conComision(238_000, 200_000, 10, true);

        assertEquals(238_000, r.getMontoBruto());
        assertEquals(200_000, r.getComisionBase());
        assertEquals(10, r.getComisionPct());
        assertEquals(20_000, r.getComisionCalculada());
        assertEquals(20_000, r.getComisionRetenida());
        assertEquals(218_000, r.getMonto(), "el vendedor conserva los 38.000 de IVA");
    }

    @Test
    void laComisionSeRedondeaAlPesoHaciaArribaDesdeLaMitad() {
        assertEquals(3, conComision(1_000, 25, 10, true).getComisionRetenida(), "2,5 sube a 3");
        assertEquals(2, conComision(1_000, 24, 10, true).getComisionRetenida(), "2,4 baja a 2");
        assertEquals(998_851, conComision(1_000_000, 11_488, 10, true).getMonto(), "1.148,8 son 1.149: el neto es un entero");
    }

    @Test
    void conPorcentajeDecimalTambienQuedanPesosEnteros() {
        RepartoPago r = conComision(119_000, 100_001, 7.5, true);

        assertEquals(7_500, r.getComisionRetenida());
        assertEquals(111_500, r.getMonto());
    }

    @Test
    void elNetoNuncaEsNegativo() {
        RepartoPago r = conComision(500, 10_000, 10, true);

        assertEquals(500, r.getComisionRetenida(), "la comisión se topa en el bruto");
        assertEquals(0, r.getMonto());
    }

    @Test
    void conBaseNegativaOSinBaseNoHayComision() {
        assertEquals(0, conComision(10_000, -5_000, 10, true).getComisionCalculada());
        RepartoPago sinBase = linea(10_000);
        ComisionPlataforma.aplicar(sinBase, null, 10, true);

        assertEquals(0, sinBase.getComisionRetenida());
        assertEquals(10_000, sinBase.getMonto());
    }

    @Test
    void conLaBanderaApagadaElMontoNoCambiaYSoloQuedaLaComisionCalculada() {
        RepartoPago r = conComision(238_000, 200_000, 10, false);

        assertEquals(238_000, r.getMonto());
        assertEquals(238_000, r.getMontoBruto());
        assertEquals(20_000, r.getComisionCalculada());
        assertEquals(0, r.getComisionRetenida());
    }

    @Test
    void conLaBanderaApagadaUnMontoConCentavosNoSeRedondea() {
        assertEquals(50_000.4, conComision(50_000.4, 42_017, 10, false).getMonto());
    }

    // ─── La dispersión paga el neto ──────────────────────────────────────────

    @Test
    void laDispersionSimuladaPagaElNeto() {
        assertEquals(new BigDecimal("218000"), new SimulatedDispersionService()
                .liquidar(conComision(238_000, 200_000, 10, true), SolicitudDispersion.automatica()).monto());
    }

    @Test
    void laDispersionManualPagaElNeto() {
        assertEquals(new BigDecimal("218000"), new ManualDispersionService().liquidar(conComision(238_000, 200_000, 10, true),
                new SolicitudDispersion("admin@demo.com", "TRF-1", null)).monto());
    }

    @Test
    void unaLineaAnteriorSeSiguePagandoCompleta() {
        // Sin desglose: nació antes del cobro, y encender la bandera no la toca
        RepartoPago anterior = RepartoPago.builder().id(1L).monto(238_000.0).build();

        assertEquals(new BigDecimal("238000"), new SimulatedDispersionService()
                .liquidar(anterior, SolicitudDispersion.automatica()).monto());
    }

    // ─── Límites del porcentaje ──────────────────────────────────────────────

    private static ParametrosMarketplace parametros(String comision) {
        ParametroRepository repo = mock(ParametroRepository.class);
        Parametro p = new Parametro();
        p.setClave("COMISION_PCT");
        p.setValor(comision);
        when(repo.findByClave("COMISION_PCT")).thenReturn(Optional.of(p));
        return new ParametrosMarketplace(repo);
    }

    @Test
    void elPorcentajeLeidoSiempreQuedaEntreCeroYCincuenta() {
        assertEquals(10, parametros("10").comisionPct());
        assertEquals(50, parametros("50").comisionPct());
        assertEquals(50, parametros("500").comisionPct());
        assertEquals(0, parametros("-5").comisionPct());
        assertEquals(10, parametros("NaN").comisionPct());
        assertEquals(10, parametros("mucho").comisionPct());
    }

    @Test
    void laBanderaVieneApagadaPorDefecto() {
        ParametrosMarketplace p = parametros("10");

        assertFalse(p.comisionRetenida());
        ReflectionTestUtils.setField(p, "comisionRetenida", true);
        assertTrue(p.comisionRetenida());
    }

    @Test
    void soloSeAceptaUnPorcentajeDeCeroACincuenta() {
        for (String valido : new String[]{"0", "10", "7,5", " 50 "}) assertTrue(ParametrosMarketplace.comisionValida(valido), valido);
        for (String invalido : new String[]{"-1", "50.5", "500", "NaN", "", "diez"}) {
            assertFalse(ParametrosMarketplace.comisionValida(invalido), invalido);
        }
        assertFalse(ParametrosMarketplace.comisionValida(null));
    }

    @Test
    void guardarUnaComisionFueraDeRangoResponde400YNoCambiaNada() {
        ParametroRepository repo = mock(ParametroRepository.class);
        ConfigController controller = new ConfigController(mock(MetodoPagoRepository.class), mock(EstadoVentaRepository.class), repo);
        Parametro cambio = new Parametro();
        cambio.setValor("500");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.actualizarParametro("COMISION_PCT", cambio));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(repo, never()).save(any());
    }
}
