package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.model.CuentaBancaria;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nombre actual, cuenta que nunca sale completa y un resumen que no depende de los filtros de la lista. */
class LiquidacionTablaServiceTest {

    private static final String NUMERO = "9876543210";

    private LiquidacionService liquidacion;
    private RepartoPagoRepository repartos;
    private TallerRepository talleres;
    private TiendaRepository tiendas;
    private LiquidacionTablaService servicio;

    @BeforeEach
    void setUp() {
        liquidacion = mock(LiquidacionService.class);
        repartos = mock(RepartoPagoRepository.class);
        talleres = mock(TallerRepository.class);
        tiendas = mock(TiendaRepository.class);
        servicio = new LiquidacionTablaService(liquidacion, repartos, talleres, tiendas);
    }

    private static RepartoPago linea(long id, String tipo, String beneficiario, String nombreGuardado) {
        return RepartoPago.builder().id(id).beneficiarioTipo(tipo).beneficiarioId(beneficiario)
                .beneficiarioNombre(nombreGuardado).monto(1000.0).estado(RepartoPago.PENDIENTE).build();
    }

    private static CuentaBancaria cuenta() {
        return CuentaBancaria.builder().banco("Davivienda").tipoCuenta("AHORROS").numeroCuenta(NUMERO)
                .titularDocumentoNumero("900123456").build();
    }

    private LiquidacionTablaService.Tabla tablaCon(RepartoPago... lineas) {
        when(liquidacion.repartosTabla(any(), any(), any())).thenReturn(new LiquidacionService.RepartosTabla(
                new Pagina<>(List.of(lineas), lineas.length, 0, 20), lineas.length, 3000));
        return servicio.tabla("", RangoFechas.de(null, null), mock(PeticionTabla.class));
    }

    @Test
    void usaElNombreActualYSiElNegocioNoExisteElGuardado() {
        when(tiendas.findAllById(any())).thenReturn(List.of(
                Tienda.builder().id("t1").nombre("Repuestos Nuevo Nombre").cuentaBancaria(cuenta()).build()));
        when(talleres.findAllById(any())).thenReturn(List.of(Taller.builder().id("a1").nombre("  ").build()));

        var filas = tablaCon(linea(1, RepartoPago.TIENDA, "t1", "Nombre Viejo"),
                linea(2, RepartoPago.TIENDA, "borrada", "Tienda Cerrada"),
                linea(3, RepartoPago.TALLER, "a1", "Taller Guardado")).repartos().items();

        assertEquals("Repuestos Nuevo Nombre", filas.get(0).beneficiario());
        assertEquals("Tienda Cerrada", filas.get(1).beneficiario());
        assertEquals("Taller Guardado", filas.get(2).beneficiario(), "un nombre vacío tampoco se muestra");
    }

    @Test
    void laCuentaSaleEnmascaradaYNuncaCompletaEnElJson() throws Exception {
        when(tiendas.findAllById(any())).thenReturn(List.of(Tienda.builder().id("t1").nombre("Tienda").cuentaBancaria(cuenta()).build()));

        var tabla = tablaCon(linea(1, RepartoPago.TIENDA, "t1", "Tienda"));
        var cuentaFila = tabla.repartos().items().get(0).cuenta();
        assertEquals("****3210", cuentaFila.numero());
        assertEquals("Davivienda", cuentaFila.banco());

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(tabla);
        assertFalse(json.contains(NUMERO), json);
        assertFalse(json.contains("900123456"), "tampoco el documento del titular");
        assertTrue(json.contains("\"beneficiarioTipo\":\"TIENDA\""), "los campos de siempre siguen en la fila: " + json);
        assertFalse(json.contains("\"reparto\":"), json);
    }

    @Test
    void sinCuentaRegistradaLaFilaLoDice() {
        when(tiendas.findAllById(any())).thenReturn(List.of(Tienda.builder().id("t1").nombre("Tienda").build()));
        assertNull(tablaCon(linea(1, RepartoPago.TIENDA, "t1", "Tienda")).repartos().items().get(0).cuenta());
    }

    @Test
    void noConsultaMongoSiLaPaginaNoTieneEseTipo() {
        tablaCon(linea(1, RepartoPago.TIENDA, "t1", "Tienda"));
        verify(talleres, never()).findAllById(any());
    }

    @Test
    void elResumenSeparaEstadosYDiceCuantoDeLoPagadoFueSimulado() {
        when(repartos.resumenSinPagar()).thenReturn(List.of(
                new Object[] { RepartoPago.PENDIENTE, 18L, 1_033_620.0 },
                new Object[] { RepartoPago.POR_LIQUIDAR, 2L, 90_000.0 }));
        when(repartos.resumenPagado(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0))).thenReturn(List.of(
                new Object[] { "SIMULADO", 19L, 1_532_325.0 },
                new Object[] { "WOMPI", 1L, 50_000.0 }));

        var r = servicio.resumen(LocalDate.of(2026, 10, 31));

        assertEquals(new LiquidacionTablaService.Cifra(2, 90_000), r.porPagar());
        assertEquals(new LiquidacionTablaService.Cifra(18, 1_033_620), r.enEspera());
        assertEquals(new LiquidacionTablaService.Cifra(0, 0), r.fallidas());
        assertEquals(new LiquidacionTablaService.Cifra(20, 1_582_325), r.pagadoMes());
        assertEquals(1_532_325, r.pagadoMesSimulado());
    }
}
