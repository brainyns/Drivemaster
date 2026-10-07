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
import java.util.List;

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
                               AvisosSolicitudServicio avisos) {
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

    public PedidoTienda noPuedoDespachar(String correoTienda, String pedidoId, String motivo) {
        String texto = recortar(motivo);
        if (texto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica por qué no puedes despachar");
        }
        Tienda tienda = tiendaDe(correoTienda);
        PedidoTienda pedido = cambiarPendiente(tienda, pedidoId, new Update()
                .set("estado", PedidoTienda.NO_DESPACHADO)
                .set("fechaNoDespacho", LocalDateTime.now())
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
        mongoTemplate.updateMulti(Query.query(Criteria.where("ordenId").is(orden.getId())
                        .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.DESPACHADO)),
                new Update().set("estado", PedidoTienda.RECIBIDO).set("fechaRecibido", LocalDateTime.now()),
                PedidoTienda.class);
        return pedidoRepository.findByOrdenIdOrderByFechaCreacionAsc(orden.getId());
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
                        .and("estado").is(PedidoTienda.PENDIENTE)),
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
