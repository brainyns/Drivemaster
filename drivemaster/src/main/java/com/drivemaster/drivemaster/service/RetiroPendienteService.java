package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedidos con retiro en la tienda que nadie recoge. Desde que la tienda lo marca listo (fechaListoRetiro):
 * a los {@value #DIAS_PRIMER_RECORDATORIO} días y a los {@value #DIAS_SEGUNDO_RECORDATORIO} se le recuerda al cliente;
 * a los {@value #DIAS_REVISION} pasa a revisión del ADMIN, que decide: seguir esperando o cancelar con reembolso. Nada
 * se cancela ni se reembolsa solo.
 *
 * Cada paso es una escritura condicional: aunque el ciclo corra dos veces a la vez, cada recordatorio sale una sola vez.
 */
@Service
public class RetiroPendienteService {

    private static final Logger log = LoggerFactory.getLogger(RetiroPendienteService.class);

    public static final int DIAS_PRIMER_RECORDATORIO = 3;
    public static final int DIAS_SEGUNDO_RECORDATORIO = 7;
    public static final int DIAS_REVISION = 15;
    static final int MAX_POR_CICLO = 100;
    static final int MAX_MOTIVO = 500;

    public static final String SEGUIR_ESPERANDO = "SEGUIR_ESPERANDO";
    public static final String REEMBOLSO = "REEMBOLSO";

    private final PedidoTiendaRepository pedidoRepository;
    private final MongoTemplate mongoTemplate;
    private final AvisosSolicitudServicio avisos;
    private final RepartoPagoRepository repartoRepository;
    private final PagoRepository pagoRepository;
    private final PagoMarketplaceService pagoMarketplace;
    private final MovimientoInventarioService movimientos;

    public RetiroPendienteService(PedidoTiendaRepository pedidoRepository, MongoTemplate mongoTemplate,
                                  AvisosSolicitudServicio avisos, RepartoPagoRepository repartoRepository,
                                  PagoRepository pagoRepository, PagoMarketplaceService pagoMarketplace,
                                  MovimientoInventarioService movimientos) {
        this.pedidoRepository = pedidoRepository;
        this.mongoTemplate = mongoTemplate;
        this.avisos = avisos;
        this.repartoRepository = repartoRepository;
        this.pagoRepository = pagoRepository;
        this.pagoMarketplace = pagoMarketplace;
        this.movimientos = movimientos;
    }

    /** Días completos desde que el pedido quedó listo para retirar. */
    public static long diasSinRetirar(PedidoTienda p, LocalDateTime ahora) {
        return p.getFechaListoRetiro() == null ? 0 : Duration.between(p.getFechaListoRetiro(), ahora).toDays();
    }

    /**
     * Un ciclo: primero la revisión (así un pedido muy viejo no recibe dos recordatorios seguidos), luego el segundo
     * recordatorio y al final el primero. Devuelve cuántos pedidos cambiaron.
     */
    public int procesar(LocalDateTime ahora) {
        int n = 0;
        for (PedidoTienda p : listosDesde(ahora.minusDays(DIAS_REVISION), Criteria.where("revisionRetiroDesde").exists(false))) {
            PedidoTienda r = marcar(p.getId(), Criteria.where("revisionRetiroDesde").exists(false),
                    new Update().set("revisionRetiroDesde", ahora).set("recordatoriosRetiro", 2));
            if (r != null) { avisos.retiroEnRevision(r, diasSinRetirar(r, ahora)); n++; }
        }
        for (PedidoTienda p : listosDesde(ahora.minusDays(DIAS_SEGUNDO_RECORDATORIO), menosDe(2))) {
            PedidoTienda r = marcar(p.getId(), menosDe(2), new Update().set("recordatoriosRetiro", 2));
            if (r != null) { avisos.recordatorioRetiro(r, diasSinRetirar(r, ahora)); n++; }
        }
        for (PedidoTienda p : listosDesde(ahora.minusDays(DIAS_PRIMER_RECORDATORIO), menosDe(1))) {
            PedidoTienda r = marcar(p.getId(), menosDe(1), new Update().set("recordatoriosRetiro", 1));
            if (r != null) { avisos.recordatorioRetiro(r, diasSinRetirar(r, ahora)); n++; }
        }
        return n;
    }

    /** Recordatorios enviados por debajo de n (sin dato = ninguno), y aún sin revisión del ADMIN. */
    private static Criteria menosDe(int n) {
        return new Criteria().andOperator(
                new Criteria().orOperator(Criteria.where("recordatoriosRetiro").is(null), Criteria.where("recordatoriosRetiro").lt(n)),
                Criteria.where("revisionRetiroDesde").exists(false));
    }

    private List<PedidoTienda> listosDesde(LocalDateTime limite, Criteria extra) {
        Query q = Query.query(new Criteria().andOperator(
                Criteria.where("estado").is(PedidoTienda.LISTO_PARA_RETIRAR),
                Criteria.where("entrega").is(PedidoTienda.RETIRO),
                Criteria.where("fechaListoRetiro").lte(limite), extra)).limit(MAX_POR_CICLO);
        return mongoTemplate.find(q, PedidoTienda.class);
    }

    private PedidoTienda marcar(String id, Criteria condicion, Update cambios) {
        return mongoTemplate.findAndModify(
                Query.query(new Criteria().andOperator(Criteria.where("id").is(id),
                        Criteria.where("estado").is(PedidoTienda.LISTO_PARA_RETIRAR), condicion)),
                cambios, FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
    }

    /** ADMIN: los retiros que llegaron a revisión, abiertos y resueltos, del más reciente al más antiguo. */
    public List<PedidoTienda> enRevision() {
        return mongoTemplate.find(Query.query(Criteria.where("revisionRetiroDesde").exists(true))
                .with(Sort.by(Sort.Direction.DESC, "revisionRetiroDesde")), PedidoTienda.class);
    }

    /**
     * El ADMIN decide un retiro en revisión, con motivo escrito: SEGUIR_ESPERANDO (el pedido sigue listo y el cliente
     * puede pasar) o REEMBOLSO (se cancela: la línea de la tienda se anula, se registra el reembolso y las unidades en
     * stock vuelven al inventario de la tienda). Después de "seguir esperando" todavía se puede cancelar; un pedido ya
     * cancelado o retirado no se vuelve a decidir.
     */
    public PedidoTienda resolver(String correoAdmin, String pedidoId, String decision, String motivo) {
        String d = decision == null ? "" : decision.trim().toUpperCase();
        if (!SEGUIR_ESPERANDO.equals(d) && !REEMBOLSO.equals(d)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La decisión debe ser SEGUIR_ESPERANDO o REEMBOLSO");
        }
        String razon = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (razon == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el motivo de tu decisión: queda registrado");
        }
        if (razon.length() > MAX_MOTIVO) razon = razon.substring(0, MAX_MOTIVO);
        PedidoTienda actual = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (actual.getRevisionRetiroDesde() == null || !PedidoTienda.LISTO_PARA_RETIRAR.equals(actual.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido no tiene un retiro pendiente de revisión");
        }

        boolean reembolsa = REEMBOLSO.equals(d);
        // Seguir esperando solo si nadie decidió aún; cancelar también después de haber decidido esperar
        Criteria sinDecidir = reembolsa
                ? new Criteria().orOperator(Criteria.where("resolucionRetiro").exists(false), Criteria.where("resolucionRetiro").is(SEGUIR_ESPERANDO))
                : Criteria.where("resolucionRetiro").exists(false);
        Update cambios = new Update().set("resolucionRetiro", d).set("fechaResolucionRetiro", LocalDateTime.now())
                .set("motivoResolucionRetiro", razon).set("retiroResueltoPor", correoAdmin);
        if (reembolsa) cambios.set("estado", PedidoTienda.NO_ENTREGADO);
        PedidoTienda pedido = mongoTemplate.findAndModify(
                Query.query(new Criteria().andOperator(Criteria.where("id").is(pedidoId),
                        Criteria.where("estado").is(PedidoTienda.LISTO_PARA_RETIRAR),
                        Criteria.where("revisionRetiroDesde").exists(true), sinDecidir)),
                cambios, FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido cambió: alguien ya lo decidió o el cliente lo retiró");
        }
        if (!reembolsa) return pedido;

        for (RepartoPago linea : repartoRepository.findByPedidoTiendaId(pedidoId)) {
            if (RepartoPago.PENDIENTE.equals(linea.getEstado())) {
                linea.setEstado(RepartoPago.ANULADO);
                linea.setNota("Retiro no recogido, cancelado por " + correoAdmin + ": " + razon);
                repartoRepository.save(linea);
            }
        }
        String detalle = "Pedido de " + pedido.getTiendaNombre() + " sin retirar, cancelado por el ADMIN: " + razon;
        if (pedido.getPagoId() != null) {
            pagoRepository.findById(pedido.getPagoId()).ifPresent(pago ->
                    pagoMarketplace.registrarReembolso(pago, pedidoId, pedido.getTotal() != null ? pedido.getTotal() : 0, detalle));
        }
        devolverAlInventario(pedido, correoAdmin);
        avisos.retiroCancelado(pedido);
        return pedido;
    }

    /** Las unidades en stock que se descontaron al comprar vuelven al inventario (los encargos nunca se descontaron). */
    private void devolverAlInventario(PedidoTienda pedido, String correoAdmin) {
        if (pedido.getItems() == null) return;
        for (CotizacionItem item : pedido.getItems()) {
            if (item.getProductoId() == null || "ENCARGO".equals(item.getDisponibilidad())
                    || item.getCantidad() == null || item.getCantidad() <= 0) continue;
            try {
                movimientos.registrarMovimiento(item.getProductoId(), "ENTRADA", item.getCantidad(),
                        "Pedido sin retirar cancelado", pedido.getId(), correoAdmin);
            } catch (RuntimeException e) {
                // El producto pudo haberse eliminado: el reembolso no se detiene por eso
                log.warn("Retiro cancelado {}: no se pudo devolver al inventario el producto {}: {}",
                        pedido.getId(), item.getProductoId(), e.getMessage());
            }
        }
    }
}
