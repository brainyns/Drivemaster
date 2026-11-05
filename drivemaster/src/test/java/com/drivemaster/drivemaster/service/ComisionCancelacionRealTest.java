package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Las cifras reales de la orden cancelada el 2026-11-02 (pago 101): la comisión de lo reembolsado debe anularse. */
class ComisionCancelacionRealTest {

    @Test
    void la_cancelacion_anula_la_comision_de_la_linea_del_taller() {
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        PagoEntity pago = new PagoEntity();
        pago.setId(101L);
        pago.setMonto(581_020.0);
        RepartoPago tienda = RepartoPago.builder().id(106L).pagoId(101L).beneficiarioTipo(RepartoPago.TIENDA).pedidoTiendaId("ped")
                .estado(RepartoPago.POR_LIQUIDAR).monto(68_670.0).montoBruto(74_970.0).comisionRetenida(6_300.0).build();
        RepartoPago taller = RepartoPago.builder().id(107L).pagoId(101L).beneficiarioTipo(RepartoPago.TALLER)
                .estado(RepartoPago.ANULADO).monto(455_445.0).montoBruto(506_050.0).comisionRetenida(50_605.0).build();
        RepartoPago plataforma = RepartoPago.builder().id(108L).pagoId(101L).beneficiarioTipo(RepartoPago.PLATAFORMA)
                .estado(RepartoPago.RETENIDA).monto(56_905.0).build();
        when(repartos.sumaComisionDePago(101L)).thenReturn(56_905.0);
        when(repartos.findByPagoId(101L)).thenReturn(List.of(tienda, taller, plataforma));

        ComisionPlataforma.anular(repartos, pago, 506_050, 7L, null);

        ArgumentCaptor<RepartoPago> guardada = ArgumentCaptor.forClass(RepartoPago.class);
        verify(repartos).save(guardada.capture());
        assertEquals(-50_605.0, guardada.getValue().getMonto());
        assertEquals("REEMBOLSO-7", guardada.getValue().getReferenciaLiquidacion());
    }

    @Test
    void anular_una_linea_limpia_la_sesion_de_jpa_para_que_la_comision_vea_el_estado_real() throws Exception {
        var modifying = RepartoPagoRepository.class.getMethod("anularPendiente", Long.class, String.class)
                .getAnnotation(org.springframework.data.jpa.repository.Modifying.class);
        org.junit.jupiter.api.Assertions.assertTrue(modifying.clearAutomatically());
        org.junit.jupiter.api.Assertions.assertTrue(modifying.flushAutomatically());
    }
}
