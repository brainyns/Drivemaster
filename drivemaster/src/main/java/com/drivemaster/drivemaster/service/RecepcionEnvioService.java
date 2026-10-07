package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Recepción de los envíos de una compra directa del catálogo. La confirma el CLIENTE ("Ya lo
 * recibí"); si no responde en PLAZO_AUTOCONFIRMACION_DIAS_HABILES días hábiles tras el despacho,
 * se confirma sola. Cualquiera de las dos habilita la liquidación de la tienda (con el evento
 * registrado). "Reportar un problema" pausa la autoconfirmación y la liquidación y alerta al ADMIN,
 * que lo resuelve (ver PedidoTiendaService.resolverProblema).
 *
 * Todas las transiciones son escrituras condicionales sobre el estado DESPACHADO: confirmar,
 * reportar y la autoconfirmación no pueden ganar a la vez.
 */
@Service
public class RecepcionEnvioService {

    private static final Logger log = LoggerFactory.getLogger(RecepcionEnvioService.class);
    static final int MAX_DETALLE = 500;
    static final int MAX_POR_CICLO = 100;
    public static final String EVENTO_CLIENTE = "RECEPCION_CONFIRMADA_POR_CLIENTE";
    public static final String EVENTO_AUTO = "AUTOCONFIRMADA_AL_VENCER_PLAZO";

    private final PedidoTiendaRepository pedidoRepository;
    private final MongoTemplate mongoTemplate;
    private final UsuarioService usuarioService;
    private final LiquidacionService liquidacion;
    private final AvisosSolicitudServicio avisos;

    public RecepcionEnvioService(PedidoTiendaRepository pedidoRepository, MongoTemplate mongoTemplate,
                                 UsuarioService usuarioService, LiquidacionService liquidacion,
                                 AvisosSolicitudServicio avisos) {
        this.pedidoRepository = pedidoRepository;
        this.mongoTemplate = mongoTemplate;
        this.usuarioService = usuarioService;
        this.liquidacion = liquidacion;
        this.avisos = avisos;
    }

    /** "Ya lo recibí": DESPACHADO → ENTREGADO y la línea de la tienda queda habilitada para liquidar. */
    public PedidoTienda confirmarRecepcion(String correoCliente, String pedidoId) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        PedidoTienda p = envioDelCliente(cliente, pedidoId);
        if (PedidoTienda.ENTREGADO.equals(p.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya confirmaste que recibiste este pedido");
        }
        if (Boolean.TRUE.equals(p.getProblemaReportado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reportaste un problema con este pedido: DriveMaster lo está revisando");
        }
        if (!PedidoTienda.DESPACHADO.equals(p.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La tienda aún no ha despachado este pedido");
        }
        PedidoTienda r = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("clienteId").is(cliente.getId())
                        .and("estado").is(PedidoTienda.DESPACHADO).and("problemaReportado").ne(true)),
                new Update().set("estado", PedidoTienda.ENTREGADO).set("fechaRecibidoCliente", LocalDateTime.now())
                        .set("recepcionAutomatica", false),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido cambió: revisa su estado y vuelve a intentar");
        }
        liquidacion.pedidoRecibido(pedidoId, EVENTO_CLIENTE);
        return r;
    }

    /**
     * "Reportar un problema" (no llegó, llegó dañado...). Solo mientras el pedido está DESPACHADO y
     * sin confirmar. Pausa la autoconfirmación y la liquidación; el ADMIN y la tienda reciben aviso.
     */
    public PedidoTienda reportarProblema(String correoCliente, String pedidoId, String detalle) {
        String texto = detalle != null ? detalle.trim() : "";
        if (texto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuéntanos qué pasó con tu pedido");
        }
        if (texto.length() > MAX_DETALLE) texto = texto.substring(0, MAX_DETALLE);
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        PedidoTienda p = envioDelCliente(cliente, pedidoId);
        if (Boolean.TRUE.equals(p.getProblemaReportado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya reportaste un problema con este pedido");
        }
        if (!PedidoTienda.DESPACHADO.equals(p.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PedidoTienda.ENTREGADO.equals(p.getEstado())
                    ? "Ya confirmaste la recepción de este pedido. Si hay un problema, escríbenos a soporte."
                    : "Solo se reporta un problema de un pedido que la tienda ya despachó");
        }
        PedidoTienda r = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("clienteId").is(cliente.getId())
                        .and("estado").is(PedidoTienda.DESPACHADO).and("problemaReportado").ne(true)),
                new Update().set("problemaReportado", true).set("problemaDetalle", texto)
                        .set("fechaProblema", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido cambió: revisa su estado y vuelve a intentar");
        }
        avisos.problemaEnvio(r, texto);
        return r;
    }

    /**
     * Confirma sola la recepción de los envíos DESPACHADOS cuyo plazo venció sin respuesta ni problema.
     * Cada pedido se toma con un findAndModify, así que dos ejecuciones simultáneas no lo procesan dos
     * veces. Devuelve cuántos confirmó.
     */
    public int autoconfirmar(LocalDateTime ahora) {
        int confirmados = 0;
        for (int i = 0; i < MAX_POR_CICLO; i++) {
            PedidoTienda p = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("origen").is(PedidoTienda.ORIGEN_CATALOGO)
                            .and("entrega").is(PedidoTienda.ENVIO)
                            .and("estado").is(PedidoTienda.DESPACHADO)
                            .and("problemaReportado").ne(true)
                            .and("autoconfirmaDespuesDe").lt(ahora)),
                    new Update().set("estado", PedidoTienda.ENTREGADO).set("fechaRecibidoCliente", ahora)
                            .set("recepcionAutomatica", true),
                    FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
            if (p == null) break;
            log.info("Pedido {} confirmado automáticamente: venció el plazo sin respuesta del cliente", p.getId());
            liquidacion.pedidoRecibido(p.getId(), EVENTO_AUTO);
            confirmados++;
        }
        return confirmados;
    }

    private PedidoTienda envioDelCliente(Usuario cliente, String pedidoId) {
        PedidoTienda p = pedidoRepository.findById(pedidoId)
                .filter(x -> x.esCatalogo() && cliente.getId().equals(x.getClienteId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!PedidoTienda.ENVIO.equals(p.getEntrega())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este pedido es para retirar en la tienda: la entrega la confirma la tienda con tu código");
        }
        return p;
    }
}
