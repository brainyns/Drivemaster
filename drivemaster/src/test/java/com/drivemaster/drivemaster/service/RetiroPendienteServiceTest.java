package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Recordatorios de los retiros que nadie recoge y la decisión del ADMIN a los 15 días (nada se reembolsa solo). */
class RetiroPendienteServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 26, 10, 0);

    private PedidoTiendaRepository pedidoRepository;
    private MongoTemplate mongo;
    private AvisosSolicitudServicio avisos;
    private RepartoPagoRepository repartos;
    private PagoRepository pagos;
    private PagoMarketplaceService pagoMarketplace;
    private MovimientoInventarioService movimientos;
    private RetiroPendienteService service;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoTiendaRepository.class);
        mongo = mock(MongoTemplate.class);
        avisos = mock(AvisosSolicitudServicio.class);
        repartos = mock(RepartoPagoRepository.class);
        pagos = mock(PagoRepository.class);
        pagoMarketplace = mock(PagoMarketplaceService.class);
        movimientos = mock(MovimientoInventarioService.class);
        service = new RetiroPendienteService(pedidoRepository, mongo, avisos, repartos, pagos, pagoMarketplace, movimientos);
    }

    private static PedidoTienda listo(String id, int diasListo) {
        CotizacionItem enStock = new CotizacionItem();
        enStock.setProductoId("p1");
        enStock.setCantidad(2);
        enStock.setDisponibilidad("DISPONIBLE");
        CotizacionItem encargo = new CotizacionItem();
        encargo.setProductoId("p2");
        encargo.setCantidad(1);
        encargo.setDisponibilidad("ENCARGO");
        return PedidoTienda.builder().id(id).estado(PedidoTienda.LISTO_PARA_RETIRAR).entrega(PedidoTienda.RETIRO)
                .origen(PedidoTienda.ORIGEN_CATALOGO).tiendaNombre("Repuestos A").total(80_000.0).pagoId(7L)
                .fechaListoRetiro(AHORA.minusDays(diasListo)).items(List.of(enStock, encargo)).build();
    }

    @Test
    void cadaPasoAvisaUnaSolaVezYUnPedidoViejoVaDirectoARevision() {
        PedidoTienda viejo = listo("viejo", 16);
        PedidoTienda semana = listo("semana", 8);
        PedidoTienda tres = listo("tres", 4);
        // Las consultas van en orden: revisión (15 días), segundo recordatorio (7) y primero (3)
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of(viejo), List.of(semana), List.of(tres));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(viejo, semana, tres);

        assertEquals(3, service.procesar(AHORA));

        verify(avisos).retiroEnRevision(viejo, 16);
        verify(avisos).recordatorioRetiro(semana, 8);
        verify(avisos).recordatorioRetiro(tres, 4);
        verify(avisos, never()).recordatorioRetiro(eq(viejo), anyLong());
        verifyNoInteractions(pagoMarketplace, movimientos);
    }

    @Test
    void siOtroCicloYaLoMarcoNoSeRepiteElAviso() {
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of(), List.of(), List.of(listo("tres", 4)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(null);

        assertEquals(0, service.procesar(AHORA));
        verifyNoInteractions(avisos);
    }

    @Test
    void cancelarConReembolsoAnulaLaLineaDevuelveElDineroYElStockMenosLosEncargos() {
        PedidoTienda enRevision = listo("ped1", 16);
        enRevision.setRevisionRetiroDesde(AHORA.minusDays(1));
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(enRevision));
        PedidoTienda cancelado = enRevision.toBuilder().estado(PedidoTienda.NO_ENTREGADO).resolucionRetiro(RetiroPendienteService.REEMBOLSO).build();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(cancelado);
        RepartoPago linea = new RepartoPago();
        linea.setEstado(RepartoPago.PENDIENTE);
        when(repartos.findByPedidoTiendaId("ped1")).thenReturn(List.of(linea));
        PagoEntity pago = new PagoEntity();
        when(pagos.findById(7L)).thenReturn(Optional.of(pago));

        PedidoTienda r = service.resolver("admin@demo.com", "ped1", "reembolso", "El cliente no respondió en 15 días");

        assertEquals(PedidoTienda.NO_ENTREGADO, r.getEstado());
        assertEquals(RepartoPago.ANULADO, linea.getEstado());
        verify(pagoMarketplace).registrarReembolso(eq(pago), eq("ped1"), eq(80_000.0), anyString());
        verify(movimientos).registrarMovimiento(eq("p1"), eq("ENTRADA"), eq(2), anyString(), eq("ped1"), eq("admin@demo.com"));
        verify(movimientos, never()).registrarMovimiento(eq("p2"), anyString(), any(), anyString(), anyString(), anyString());
        verify(avisos).retiroCancelado(cancelado);
    }

    @Test
    void seguirEsperandoNoReembolsaNiTocaElStock() {
        PedidoTienda enRevision = listo("ped1", 16);
        enRevision.setRevisionRetiroDesde(AHORA.minusDays(1));
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(enRevision));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(PedidoTienda.class)))
                .thenReturn(enRevision.toBuilder().resolucionRetiro(RetiroPendienteService.SEGUIR_ESPERANDO).build());

        PedidoTienda r = service.resolver("admin@demo.com", "ped1", "SEGUIR_ESPERANDO", "El cliente avisó que pasa el sábado");

        assertEquals(PedidoTienda.LISTO_PARA_RETIRAR, r.getEstado());
        verifyNoInteractions(pagoMarketplace, movimientos, repartos);
        verify(avisos, never()).retiroCancelado(any());
    }

    @Test
    void laDecisionExigeMotivoYUnRetiroEnRevision() {
        ResponseStatusException sinMotivo = assertThrows(ResponseStatusException.class,
                () -> service.resolver("admin@demo.com", "ped1", "REEMBOLSO", "  "));
        assertEquals(HttpStatus.BAD_REQUEST, sinMotivo.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.resolver("admin@demo.com", "ped1", "REGALAR", "x"));

        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(listo("ped1", 4))); // aún no llega a revisión
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.resolver("admin@demo.com", "ped1", "REEMBOLSO", "motivo"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verifyNoInteractions(pagoMarketplace);
    }

    @Test
    void losDiasSinRetirarSeCuentanDesdeQueQuedoListo() {
        assertEquals(7, RetiroPendienteService.diasSinRetirar(listo("x", 7), AHORA));
        assertEquals(0, RetiroPendienteService.diasSinRetirar(PedidoTienda.builder().build(), AHORA));
    }
}
