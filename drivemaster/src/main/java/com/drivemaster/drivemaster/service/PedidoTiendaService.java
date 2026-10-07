package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pedidos de repuestos a las tiendas (uno por tienda y cotización pagada).
 *
 * "No puedo despachar": el pedido queda NO_DESPACHADO, el reparto de la tienda se
 * anula, se registra el reembolso de su parte y se alerta al ADMIN. La elección de
 * otra tienda llega en la Fase 4. El stock descontado no se repone solo: si la
 * tienda no tenía el repuesto, su inventario estaba mal y debe ajustarlo ella.
 */
@Service
public class PedidoTiendaService {

    private static final int MAX_TEXTO = 500;

    private final PedidoTiendaRepository pedidoRepository;
    private final TiendaRepository tiendaRepository;
    private final OrdenServicioRepository ordenRepository;
    private final PagoRepository pagoRepository;
    private final RepartoPagoRepository repartoRepository;
    private final MongoTemplate mongoTemplate;
    private final UsuarioService usuarioService;
    private final TallerService tallerService;
    private final TransicionOrdenService transiciones;
    private final PagoMarketplaceService pagoMarketplace;
    private final AvisosSolicitudServicio avisos;
    private final LiquidacionService liquidacion;

    public PedidoTiendaService(PedidoTiendaRepository pedidoRepository,
                               TiendaRepository tiendaRepository,
                               OrdenServicioRepository ordenRepository,
                               PagoRepository pagoRepository,
                               RepartoPagoRepository repartoRepository,
                               MongoTemplate mongoTemplate,
                               UsuarioService usuarioService,
                               TallerService tallerService,
                               TransicionOrdenService transiciones,
                               PagoMarketplaceService pagoMarketplace,
                               AvisosSolicitudServicio avisos,
                               LiquidacionService liquidacion) {
        this.liquidacion = liquidacion;
        this.pedidoRepository = pedidoRepository;
        this.tiendaRepository = tiendaRepository;
        this.ordenRepository = ordenRepository;
        this.pagoRepository = pagoRepository;
        this.repartoRepository = repartoRepository;
        this.mongoTemplate = mongoTemplate;
        this.usuarioService = usuarioService;
        this.tallerService = tallerService;
        this.transiciones = transiciones;
        this.pagoMarketplace = pagoMarketplace;
        this.avisos = avisos;
    }

    // ─── Tienda ──────────────────────────────────────────────────────────────

    public List<PedidoTienda> misPedidos(String correoTienda) {
        return pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc(tiendaDe(correoTienda).getId());
    }

    /**
     * La tienda empieza a conseguir un repuesto por encargo: PENDIENTE → EN_PREPARACION.
     * Solo pedidos con al menos un ítem por encargo; los de stock se despachan directo.
     * El plazo de despacho no cambia (corre desde el pago).
     */
    public PedidoTienda iniciarPreparacion(String correoTienda, String pedidoId) {
        Tienda tienda = tiendaDe(correoTienda);
        PedidoTienda actual = pedidoRepository.findById(pedidoId)
                .filter(p -> tienda.getId().equals(p.getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        boolean hayEncargo = actual.getItems() != null
                && actual.getItems().stream().anyMatch(i -> "ENCARGO".equals(i.getDisponibilidad()));
        if (!hayEncargo) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo los pedidos con repuestos por encargo pasan por preparación: despáchalo directamente");
        }
        PedidoTienda pedido = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("tiendaId").is(tienda.getId())
                        .and("estado").is(PedidoTienda.PENDIENTE)),
                new Update().set("estado", PedidoTienda.EN_PREPARACION).set("fechaPreparacion", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente");
        }
        ordenRepository.findById(pedido.getOrdenId()).ifPresent(o -> {
            try {
                transiciones.registrar(o, new Actor("TIENDA", tienda.getId(), tienda.getNombre()),
                        tienda.getNombre() + " está preparando el encargo", null, null);
            } catch (ResponseStatusException ignored) {
                // Informativo
            }
        });
        return pedido;
    }

    public PedidoTienda despachar(String correoTienda, String pedidoId, String nota) {
        Tienda tienda = tiendaDe(correoTienda);
        PedidoTienda pedido = cambiarPendiente(tienda, pedidoId, new Update()
                .set("estado", PedidoTienda.DESPACHADO)
                .set("fechaDespacho", LocalDateTime.now())
                .set("notaDespacho", recortar(nota)));
        ordenRepository.findById(pedido.getOrdenId()).ifPresent(o -> {
            try {
                transiciones.registrar(o, new Actor("TIENDA", tienda.getId(), tienda.getNombre()),
                        tienda.getNombre() + " despachó los repuestos", null, null);
            } catch (ResponseStatusException ignored) {
                // El evento es informativo: si la orden cambió justo ahora no pasa nada
            }
        });
        return pedido;
    }

    /**
     * tipoMotivo: SIN_REPUESTO ("No tengo el repuesto", detalle opcional) u OTRO
     * (detalle obligatorio).
     */
    public PedidoTienda noPuedoDespachar(String correoTienda, String pedidoId, String tipoMotivo, String detalle) {
        String tipo = tipoMotivo != null ? tipoMotivo.trim().toUpperCase() : "";
        String nota = recortar(detalle);
        if (PedidoTienda.MOTIVO_OTRO.equals(tipo) && nota == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuéntanos por qué no puedes despachar");
        }
        if (!PedidoTienda.MOTIVO_SIN_REPUESTO.equals(tipo) && !PedidoTienda.MOTIVO_OTRO.equals(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige el motivo: no tengo el repuesto u otro motivo");
        }
        String texto = recortar(PedidoTienda.MOTIVO_SIN_REPUESTO.equals(tipo)
                ? "No tengo el repuesto" + (nota != null ? ": " + nota : "")
                : nota);
        Tienda tienda = tiendaDe(correoTienda);
        PedidoTienda pedido = cambiarPendiente(tienda, pedidoId, new Update()
                .set("estado", PedidoTienda.NO_DESPACHADO)
                .set("fechaNoDespacho", LocalDateTime.now())
                .set("tipoMotivoNoDespacho", tipo)
                .set("motivoNoDespacho", texto));

        for (RepartoPago r : repartoRepository.findByPedidoTiendaId(pedido.getId())) {
            if (RepartoPago.PENDIENTE.equals(r.getEstado())) {
                r.setEstado(RepartoPago.ANULADO);
                r.setNota("La tienda no despachó: " + texto);
                repartoRepository.save(r);
            }
        }
        pagoRepository.findById(pedido.getPagoId()).ifPresent(pago -> pagoMarketplace.registrarReembolso(pago,
                pedido.getId(), pedido.getTotal(), tienda.getNombre() + " no pudo despachar: " + texto));

        OrdenServicio orden = ordenRepository.findById(pedido.getOrdenId()).orElse(null);
        if (orden != null) {
            try {
                orden = transiciones.registrar(orden, new Actor("TIENDA", tienda.getId(), tienda.getNombre()),
                        tienda.getNombre() + " no puede despachar los repuestos: " + texto, null, null);
            } catch (ResponseStatusException ignored) {
                // Solo informativo
            }
            avisos.pedidoNoDespachado(orden, pedido);
        }
        return pedido;
    }

    // ─── Taller ──────────────────────────────────────────────────────────────

    /**
     * El taller confirma que recibió los repuestos. Vale aunque la tienda no haya
     * marcado el despacho (el taller los tiene en la mano); los no despachados no
     * cambian.
     */
    public List<PedidoTienda> repuestosRecibidos(String correoTaller, String ordenId) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = ordenMarketplace(ordenId);
        if (!taller.getId().equals(orden.getTallerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no está asignada a tu taller");
        }
        if (!EstadoOrden.REPUESTOS_PEDIDOS.equals(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud no está esperando repuestos");
        }
        transiciones.transicionar(orden, EstadoOrden.REPUESTOS_RECIBIDOS,
                new Actor(EstadoOrden.TALLER, taller.getId(), taller.getNombre()), "Repuestos recibidos en el taller",
                Criteria.where("tallerId").is(taller.getId()), null);
        Map<String, String> estadoPrevio = new HashMap<>();
        for (PedidoTienda p : pedidoRepository.findByOrdenIdOrderByFechaCreacionAsc(orden.getId())) {
            estadoPrevio.put(p.getId(), p.getEstado());
        }
        mongoTemplate.updateMulti(Query.query(Criteria.where("ordenId").is(orden.getId())
                        .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION, PedidoTienda.DESPACHADO)),
                new Update().set("estado", PedidoTienda.RECIBIDO).set("fechaRecibido", LocalDateTime.now()),
                PedidoTienda.class);
        List<PedidoTienda> pedidos = pedidoRepository.findByOrdenIdOrderByFechaCreacionAsc(orden.getId());
        // Recepción confirmada: la línea de cada tienda queda lista para liquidar (también si no marcó despacho)
        for (PedidoTienda p : pedidos) {
            if (!PedidoTienda.RECIBIDO.equals(p.getEstado())) continue;
            String previo = estadoPrevio.get(p.getId());
            String evento = "RECEPCION_CONFIRMADA_TALLER" + (PedidoTienda.PENDIENTE.equals(previo)
                    ? " (la tienda no había marcado el despacho)" : "");
            liquidacion.pedidoRecibido(p.getId(), evento);
        }
        return pedidos;
    }

    // ─── Consulta por orden ──────────────────────────────────────────────────

    /** Pedidos de una orden: cliente dueño, taller asignado o ADMIN. */
    public List<PedidoTienda> deOrden(String correo, String ordenId) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);
        OrdenServicio orden = ordenMarketplace(ordenId);
        String actor = EstadoOrden.actorDeRol(usuario.getRol());
        boolean acceso = EstadoOrden.ADMIN.equals(actor)
                || (EstadoOrden.CLIENTE.equals(actor) && usuario.getId().equals(orden.getUsuarioId()))
                || (EstadoOrden.TALLER.equals(actor)
                    && tallerService.obtenerPorUsuario(correo).getId().equals(orden.getTallerId()));
        if (!acceso) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a esta solicitud");
        }
        return pedidoRepository.findByOrdenIdOrderByFechaCreacionAsc(orden.getId());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private PedidoTienda cambiarPendiente(Tienda tienda, String pedidoId, Update cambios) {
        PedidoTienda pedido = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("tiendaId").is(tienda.getId())
                        .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION)),
                cambios, FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (pedido == null) {
            boolean existe = pedidoRepository.findById(pedidoId).filter(p -> tienda.getId().equals(p.getTiendaId())).isPresent();
            throw existe ? new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya no está pendiente")
                    : new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        return pedido;
    }

    private Tienda tiendaDe(String correo) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);
        return tiendaRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu cuenta no tiene una tienda"));
    }

    private OrdenServicio ordenMarketplace(String id) {
        return ordenRepository.findById(id)
                .filter(o -> EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
    }

    private static String recortar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        return t.length() > MAX_TEXTO ? t.substring(0, MAX_TEXTO) : t;
    }
}
