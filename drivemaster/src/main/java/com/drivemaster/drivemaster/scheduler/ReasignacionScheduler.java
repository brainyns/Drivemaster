package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.service.AsignacionTallerService;
import com.drivemaster.drivemaster.service.AvisosSolicitudServicio;
import com.drivemaster.drivemaster.service.TransicionOrdenService;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vencimientos del flujo de solicitudes, cada 5 minutos (configurable con
 * marketplace.vencimientos.delay-ms):
 *  1. Talleres que no respondieron a tiempo → se reasigna la solicitud.
 *  2. Clientes que no pagaron el diagnóstico a tiempo → la solicitud se cancela.
 *  3. Solicitudes que quedaron sin asignar (ENVIADA/REASIGNADA, por un fallo a
 *     mitad de camino) → se reintenta la asignación.
 *
 * No hay doble procesamiento: fixedDelay evita ejecuciones solapadas y cada cambio
 * es una escritura condicional (TransicionOrdenService). Si otra acción ganó la
 * carrera, la escritura no aplica, se recibe 409 y la solicitud se omite.
 */
@Component
public class ReasignacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReasignacionScheduler.class);
    private static final String MOTIVO_SIN_RESPUESTA = "El taller no respondió a tiempo";
    private static final String MOTIVO_PAGO_VENCIDO = "No se pagó el diagnóstico dentro del plazo";
    // Margen para no competir con una asignación que esté en curso en este momento.
    private static final int MINUTOS_GRACIA_SIN_ASIGNAR = 2;

    private final MongoTemplate mongoTemplate;
    private final AsignacionTallerService asignacion;
    private final TransicionOrdenService transiciones;
    private final AvisosSolicitudServicio avisos;

    public ReasignacionScheduler(MongoTemplate mongoTemplate,
                                 AsignacionTallerService asignacion,
                                 TransicionOrdenService transiciones,
                                 AvisosSolicitudServicio avisos) {
        this.mongoTemplate = mongoTemplate;
        this.asignacion = asignacion;
        this.transiciones = transiciones;
        this.avisos = avisos;
    }

    @Scheduled(fixedDelayString = "${marketplace.vencimientos.delay-ms:300000}",
               initialDelayString = "${marketplace.vencimientos.initial-delay-ms:60000}")
    public void procesarVencimientos() {
        LocalDateTime ahora = LocalDateTime.now();

        procesar("sin respuesta del taller", Query.query(Criteria.where("flujo").is(EstadoOrden.FLUJO_MARKETPLACE)
                        .and("estado").is(EstadoOrden.ASIGNADA_A_TALLER)
                        .and("aceptadaPorTaller").ne(true)
                        .and("fechaLimiteRespuesta").lt(ahora)),
                orden -> asignacion.reasignar(orden, Actor.SISTEMA, MOTIVO_SIN_RESPUESTA));

        procesar("pago de diagnóstico vencido", Query.query(Criteria.where("flujo").is(EstadoOrden.FLUJO_MARKETPLACE)
                        .and("estado").is(EstadoOrden.ASIGNADA_A_TALLER)
                        .and("aceptadaPorTaller").is(true)
                        .and("pagoDiagnosticoEstado").is(EstadoOrden.PAGO_PENDIENTE)
                        .and("fechaLimitePagoDiagnostico").lt(ahora)),
                this::cancelarPorPagoVencido);

        procesar("sin asignar", Query.query(Criteria.where("flujo").is(EstadoOrden.FLUJO_MARKETPLACE)
                        .and("estado").in(EstadoOrden.ENVIADA, EstadoOrden.REASIGNADA)
                        .and("fechaCreacion").lt(ahora.minusMinutes(MINUTOS_GRACIA_SIN_ASIGNAR))),
                asignacion::asignarSiguiente);
    }

    private void cancelarPorPagoVencido(OrdenServicio orden) {
        String tallerId = orden.getTallerId();
        OrdenServicio cancelada = transiciones.transicionar(orden, EstadoOrden.CANCELADA, Actor.SISTEMA,
                MOTIVO_PAGO_VENCIDO,
                Criteria.where("pagoDiagnosticoEstado").is(EstadoOrden.PAGO_PENDIENTE),
                new Update().set("pagoDiagnosticoEstado", EstadoOrden.PAGO_VENCIDO)
                        .set("motivoCierre", MOTIVO_PAGO_VENCIDO));
        avisos.solicitudCancelada(cancelada, MOTIVO_PAGO_VENCIDO);
        avisos.solicitudRetirada(tallerId, cancelada, MOTIVO_PAGO_VENCIDO);
    }

    private void procesar(String tipo, Query query, Consumer<OrdenServicio> accion) {
        List<OrdenServicio> ordenes;
        try {
            ordenes = mongoTemplate.find(query, OrdenServicio.class);
        } catch (Exception e) {
            log.error("Vencimientos ({}): no se pudo consultar", tipo, e);
            return;
        }
        for (OrdenServicio orden : ordenes) {
            try {
                accion.accept(orden);
                log.info("Vencimientos ({}): solicitud {} procesada", tipo, orden.getId());
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    // Otra acción la cambió primero (taller aceptó, cliente canceló...): se omite.
                    log.debug("Vencimientos ({}): solicitud {} ya cambió, se omite", tipo, orden.getId());
                } else {
                    log.warn("Vencimientos ({}): solicitud {}: {}", tipo, orden.getId(), e.getReason());
                }
            } catch (Exception e) {
                log.error("Vencimientos ({}): error con la solicitud {}", tipo, orden.getId(), e);
            }
        }
    }
}
