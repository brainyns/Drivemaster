package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Detalle de una solicitud para el SUPERADMIN: de solo lectura y con el correo enmascarado. */
class DetalleOrdenPlataformaTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
    private final ReembolsoRepository reembolsos = mock(ReembolsoRepository.class);
    private final PlataformaDetalleService servicio = new PlataformaDetalleService(mongo, pagos, repartos, reembolsos);

    @Test
    void reuneLasPartesElTiempoElDineroYLosPedidos() {
        LocalDateTime dia = LocalDateTime.of(2026, 10, 28, 9, 0);
        OrdenServicio orden = OrdenServicio.builder().id("o1").usuarioId("u1").tallerId("a1").tallerNombre("Taller Guardado")
                .vehiculoDescripcion("Spark GT 2021").estado("REPUESTOS_RECIBIDOS").sintomas("Chirrido al frenar").total(178_500.0)
                .fechaCreacion(dia)
                .eventos(List.of(
                        EventoOrden.builder().estado("PAGADA").fecha(dia.plusDays(1)).actorNombre("Laura").build(),
                        EventoOrden.builder().estado("ENVIADA").fecha(dia).nota("Solicitud creada").build(),
                        EventoOrden.builder().estado("SIN_FECHA").build()))
                .build();
        when(mongo.findById("o1", OrdenServicio.class)).thenReturn(orden);
        when(mongo.findById("u1", Usuario.class)).thenReturn(Usuario.builder().id("u1").nombre("Laura Gómez")
                .correo("laura.gomez@correo.co").telefono("3004417721").build());
        when(mongo.findById("a1", Taller.class)).thenReturn(Taller.builder().id("a1").nombre("Taller Actual").telefono("6041").ciudad("Medellín").build());
        when(mongo.find(any(Query.class), eq(PedidoTienda.class))).thenReturn(List.of(
                PedidoTienda.builder().id("p1").tiendaNombre("Repuestos Sur").estado("RECIBIDO").total(34_510.0).fechaCreacion(dia).build()));
        PagoEntity pago = new PagoEntity();
        pago.setId(7L);
        pago.setReferencia("DM-1");
        pago.setMonto(178_500.0);
        pago.setConcepto("COTIZACION");
        when(pagos.findByOrdenServicioIdOrderByIdAsc("o1")).thenReturn(List.of(pago));
        when(repartos.findByOrdenServicioId("o1")).thenReturn(List.of(RepartoPago.builder().beneficiarioTipo("TALLER")
                .beneficiarioNombre("Taller Actual").monto(15_000.0).estado(RepartoPago.PENDIENTE).build()));
        when(reembolsos.findByOrdenServicioId("o1")).thenReturn(List.of());

        var d = servicio.orden("o1");

        assertEquals("Taller Actual", d.taller().nombre());
        assertFalse(d.cliente().correo().contains("laura.gomez"), "el correo va enmascarado");
        assertEquals(List.of("ENVIADA", "PAGADA"), d.lineaDeTiempo().stream().map(e -> e.etiqueta()).toList(),
                "en orden y sin los eventos sin fecha");
        assertEquals("Chirrido al frenar", d.detalle());
        assertEquals(1, d.pagos().size());
        assertEquals("DM-1", d.pagos().get(0).referencia());
        assertEquals(1, d.reparto().size());
        assertEquals("p1", d.pedidos().get(0).id());
    }

    @Test
    void sinEventosLaLineaDeTiempoEmpiezaEnLaCreacion() {
        LocalDateTime dia = LocalDateTime.of(2026, 10, 1, 8, 0);
        when(mongo.findById("o2", OrdenServicio.class)).thenReturn(OrdenServicio.builder().id("o2").fechaCreacion(dia)
                .productoNombre("Cambio de aceite").estado("FINALIZADA").build());

        var d = servicio.orden("o2");

        assertEquals(1, d.lineaDeTiempo().size());
        assertEquals("Cambio de aceite", d.detalle());
        assertEquals(null, d.cliente());
        assertEquals(null, d.taller());
    }

    @Test
    void unaOrdenInexistenteResponde404() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.orden("no"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}
