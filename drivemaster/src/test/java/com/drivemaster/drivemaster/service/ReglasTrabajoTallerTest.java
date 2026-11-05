package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReglasTrabajoTallerTest {

    private CotizacionRepository cotizaciones;
    private PedidoTiendaRepository pedidos;
    private ReglasTrabajoTaller reglas;

    @BeforeEach
    void setUp() {
        cotizaciones = mock(CotizacionRepository.class);
        pedidos = mock(PedidoTiendaRepository.class);
        reglas = new ReglasTrabajoTaller(cotizaciones, pedidos);
    }

    private static PedidoTienda pedido(String estado) {
        return PedidoTienda.builder().estado(estado).build();
    }

    private void cotizacion(String estado, double saldo) {
        when(cotizaciones.findById("c1")).thenReturn(Optional.of(Cotizacion.builder().id("c1").estado(estado).totalAPagar(saldo).items(List.of()).build()));
    }

    @Test
    void conPagoRegistradoPasaSinConsultarLaCotizacion() {
        assertDoesNotThrow(() -> reglas.exigirPago(OrdenServicio.builder().pagoCotizacionId(9L).build()));
    }

    @Test
    void unaAprobadaSinSaldoNoNecesitaPago() {
        cotizacion(Cotizacion.APROBADA, 0);
        assertDoesNotThrow(() -> reglas.exigirPago(OrdenServicio.builder().cotizacionId("c1").build()));
    }

    @Test
    void sinPagoConSaldoOSinCotizacionSeRechaza() {
        cotizacion(Cotizacion.APROBADA, 164_200);
        for (OrdenServicio o : List.of(OrdenServicio.builder().cotizacionId("c1").build(), OrdenServicio.builder().build())) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> reglas.exigirPago(o));
            assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        }
        cotizacion(Cotizacion.ANULADA, 0);
        assertThrows(ResponseStatusException.class, () -> reglas.exigirPago(OrdenServicio.builder().cotizacionId("c1").build()));
    }

    @Test
    void siNingunRepuestoLlegoNoSeInstala() {
        when(pedidos.findByOrdenIdOrderByFechaCreacionAsc("o1")).thenReturn(List.of(pedido(PedidoTienda.NO_DESPACHADO), pedido(PedidoTienda.NO_DESPACHADO)));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> reglas.exigirRepuestos(OrdenServicio.builder().id("o1").build()));

        assertEquals(ReglasTrabajoTaller.SIN_REPUESTOS, e.getReason());
    }

    @Test
    void unaEntregaParcialOSoloManoDeObraSiSeInstala() {
        when(pedidos.findByOrdenIdOrderByFechaCreacionAsc("o1")).thenReturn(List.of(pedido(PedidoTienda.NO_DESPACHADO), pedido(PedidoTienda.RECIBIDO)));
        assertDoesNotThrow(() -> reglas.exigirRepuestos(OrdenServicio.builder().id("o1").build()));

        when(pedidos.findByOrdenIdOrderByFechaCreacionAsc("o1")).thenReturn(List.of());
        assertDoesNotThrow(() -> reglas.exigirRepuestos(OrdenServicio.builder().id("o1").build()));
    }
}
