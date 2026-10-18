package com.drivemaster.drivemaster.scheduler;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.service.AvisosSolicitudServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Alerta al ADMIN cuando un pedido a una tienda pasa su plazo de despacho sin que la
 * tienda lo haya despachado ni declarado "no puedo despachar". Una sola alerta por
 * pedido: se "reclama" con un findAndModify (alertaAtrasoEnviada = true), así que dos
 * ejecuciones simultáneas no duplican el aviso. Si el pedido ya salió (DESPACHADO,
 * NO_DESPACHADO, RECIBIDO) deja de coincidir y no se alerta.
 *
 * Los pedidos anteriores a la Fase 4-B no tienen plazo y no se evalúan.
 */
// Solo en el servidor que ejecuta las tareas de fondo: ver TAREAS_PROGRAMADAS_ENABLED en application.properties.
@ConditionalOnProperty(name = "drivemaster.tareas-programadas.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class DespachoAtrasadoScheduler {

    private static final Logger log = LoggerFactory.getLogger(DespachoAtrasadoScheduler.class);
    private static final int MAX_POR_CICLO = 100;

    private final MongoTemplate mongoTemplate;
    private final AvisosSolicitudServicio avisos;

    public DespachoAtrasadoScheduler(MongoTemplate mongoTemplate, AvisosSolicitudServicio avisos) {
        this.mongoTemplate = mongoTemplate;
        this.avisos = avisos;
    }

    @Scheduled(fixedDelayString = "${marketplace.despachos.delay-ms:900000}",
               initialDelayString = "${marketplace.despachos.initial-delay-ms:90000}")
    public void alertarAtrasos() {
        try {
            int alertados = alertarAtrasos(LocalDateTime.now());
            if (alertados > 0) log.info("Despachos atrasados: {} alerta(s) al ADMIN", alertados);
        } catch (Exception e) {
            log.error("Despachos atrasados: no se pudo procesar", e);
        }
    }

    /** Visible para pruebas: alerta los pedidos vencidos a la hora dada y devuelve cuántos. */
    public int alertarAtrasos(LocalDateTime ahora) {
        int alertados = 0;
        for (int i = 0; i < MAX_POR_CICLO; i++) {
            PedidoTienda p = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION)
                            .and("plazoDespachoHasta").lt(ahora)
                            .and("alertaAtrasoEnviada").ne(true)),
                    new Update().set("alertaAtrasoEnviada", true),
                    FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
            if (p == null) break;
            avisos.despachoAtrasado(p);
            alertados++;
        }
        return alertados;
    }
}
