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
import com.drivemaster.drivemaster.util.CodigoRetiro;
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
    private final ParametrosMarketplace parametros;

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
                               LiquidacionService liquidacion,
                               ParametrosMarketplace parametros) {
        this.liquidacion = liquidacion;
        this.parametros = parametros;
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
     * Lo que la tienda puede ver de un pedido. De la persona que compró solo lo necesario para despachar:
     * nunca sus identificadores internos (cliente, compra, pago) ni su nombre completo (en un retiro, solo nombre e
     * inicial del apellido, para reconocerlo en el mostrador); y la dirección,
     * el teléfono y quien recibe solo si el pedido es con envío (en retiro esos datos ni se guardan).
     * Los pedidos de talleres conservan los datos del taller, que la tienda necesita para entregar.
     */
    public static PedidoTienda paraTienda(PedidoTienda p) {
        if (p == null) return null;
        // En un retiro la tienda ve solo el nombre y la inicial del apellido ("Laura M."), para reconocer a quien llega
        String nombreRetiro = PedidoTienda.RETIRO.equals(p.getEntrega()) ? nombreEInicial(p.getClienteNombre()) : null;
        PedidoTienda vista = p.toBuilder().clienteId(null).clienteNombre(nombreRetiro).compraId(null).pagoId(null).build();
        if (!PedidoTienda.ENVIO.equals(p.getEntrega())) {
            vista.setEnvioReceptor(null);
            vista.setEnvioTelefono(null);
            vista.setEnvioDireccion(null);
            vista.setEnvioCiudad(null);
            vista.setEnvioIndicaciones(null);
        }
        return vista;
    }

    /**
     * "Laura Gómez" → "Laura G.". El usuario guarda el nombre en un solo campo: con hasta tres palabras el apellido es
     * la segunda ("Laura Gómez Pérez" → "Laura G."); con cuatro o más, la penúltima ("Laura María Gómez Pérez" →
     * "Laura G."). Una sola palabra queda tal cual; vacío → null.
     */
    static String nombreEInicial(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length == 1) return partes[0];
        String apellido = partes[partes.length >= 4 ? partes.length - 2 : 1];
        return partes[0] + " " + apellido.substring(0, apellido.offsetByCodePoints(0, 1)).toUpperCase() + ".";
    }

    /** ADMIN: pedidos con un problema reportado, abiertos y resueltos. */
    public List<PedidoTienda> problemasDeEnvio() {
        return pedidoRepository.findByProblemaReportadoTrueOrderByFechaProblemaDesc();
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
        ordenDe(pedido).ifPresent(o -> {
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
        PedidoTienda previo = pedidoRepository.findById(pedidoId).filter(p -> tienda.getId().equals(p.getTiendaId())).orElse(null);
        if (previo != null && PedidoTienda.RETIRO.equals(previo.getEntrega())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este pedido es para retirar en tu tienda: márcalo como \"Listo para retirar\"");
        }
        LocalDateTime ahora = LocalDateTime.now();
        Update cambios = new Update()
                .set("estado", PedidoTienda.DESPACHADO)
                .set("fechaDespacho", ahora)
                .set("notaDespacho", recortar(nota));
        // Envío del catálogo: el cliente confirma la recepción; si no responde, se confirma sola a los N días hábiles
        boolean envioCatalogo = previo != null && previo.esCatalogo() && PedidoTienda.ENVIO.equals(previo.getEntrega());
        int diasAuto = parametros.plazoAutoconfirmacionDiasHabiles();
        if (envioCatalogo) {
            cambios.set("autoconfirmaDespuesDe", com.drivemaster.drivemaster.util.PlazoHabil.sumarDiasHabiles(ahora, diasAuto));
        }
        PedidoTienda pedido = cambiarPendiente(tienda, pedidoId, cambios);
        if (envioCatalogo) avisos.pedidoEnviado(pedido, diasAuto);
        ordenDe(pedido).ifPresent(o -> {
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

        if (pedido.esCatalogo()) {
            // Compra directa: no hay orden ni taller. Se avisa al cliente (reembolso de esta tienda) y al ADMIN.
            avisos.pedidoCatalogoNoDespachado(pedido);
            return pedido;
        }
        OrdenServicio orden = ordenDe(pedido).orElse(null);
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

    // ─── Problemas con envíos del catálogo (ADMIN) ───────────────────────────

    public static final String RESOLVER_ENTREGA = "CONFIRMAR_ENTREGA";
    public static final String RESOLVER_REEMBOLSO = "REEMBOLSAR";

    /**
     * El ADMIN resuelve un problema reportado por el cliente: CONFIRMAR_ENTREGA (sí llegó: el pedido
     * queda ENTREGADO y la línea de la tienda se habilita) o REEMBOLSAR (no llegó: se anula la línea
     * de la tienda y se registra el reembolso de ese pedido). Solo si el problema sigue abierto.
     */
    public PedidoTienda resolverProblema(String correoAdmin, String pedidoId, String resolucion, String motivo) {
        String r = resolucion != null ? resolucion.trim().toUpperCase() : "";
        if (!RESOLVER_ENTREGA.equals(r) && !RESOLVER_REEMBOLSO.equals(r)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La resolución debe ser CONFIRMAR_ENTREGA o REEMBOLSAR");
        }
        String razon = recortar(motivo);
        if (razon == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el motivo de tu decisión: queda registrado");
        }
        PedidoTienda actual = pedidoRepository.findById(pedidoId).filter(PedidoTienda::esCatalogo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!Boolean.TRUE.equals(actual.getProblemaReportado()) || !PedidoTienda.DESPACHADO.equals(actual.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido no tiene un problema abierto");
        }
        boolean reembolsa = RESOLVER_REEMBOLSO.equals(r);
        PedidoTienda pedido = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("estado").is(PedidoTienda.DESPACHADO)
                        .and("problemaReportado").is(true).and("resolucionProblema").exists(false)),
                new Update().set("estado", reembolsa ? PedidoTienda.NO_ENTREGADO : PedidoTienda.ENTREGADO)
                        .set("resolucionProblema", reembolsa ? "REEMBOLSO" : "ENTREGA_CONFIRMADA_POR_ADMIN")
                        .set("fechaResolucionProblema", LocalDateTime.now())
                        .set("motivoResolucion", razon)
                        .set("resueltoPor", correoAdmin),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alguien más ya resolvió este problema");
        }
        if (reembolsa) {
            for (RepartoPago linea : repartoRepository.findByPedidoTiendaId(pedidoId)) {
                if (RepartoPago.PENDIENTE.equals(linea.getEstado())) {
                    linea.setEstado(RepartoPago.ANULADO);
                    linea.setNota(recortar("Problema con el envío resuelto con reembolso por " + correoAdmin + ": " + razon));
                    repartoRepository.save(linea);
                }
            }
            pagoRepository.findById(pedido.getPagoId()).ifPresent(pago -> pagoMarketplace.registrarReembolso(pago, pedidoId,
                    pedido.getTotal(), "El envío de " + pedido.getTiendaNombre() + " no llegó (resuelto por el ADMIN): " + razon));
        } else {
            liquidacion.pedidoRecibido(pedidoId, "ENTREGA_CONFIRMADA_POR_ADMIN");
        }
        avisos.problemaEnvioResuelto(pedido, reembolsa);
        return pedido;
    }

    // ─── Compras del catálogo con retiro en la tienda ────────────────────────

    static final int MAX_INTENTOS_CODIGO = 5;
    static final int BLOQUEO_CODIGO_MINUTOS = 15;

    /** El pedido está preparado y el cliente ya puede pasar por él: PENDIENTE / EN_PREPARACION → LISTO_PARA_RETIRAR. */
    public PedidoTienda listoParaRetirar(String correoTienda, String pedidoId) {
        Tienda tienda = tiendaDe(correoTienda);
        PedidoTienda actual = pedidoRepository.findById(pedidoId)
                .filter(p -> p.esCatalogo() && tienda.getId().equals(p.getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!PedidoTienda.RETIRO.equals(actual.getEntrega())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido es con envío: márcalo como despachado");
        }
        PedidoTienda pedido = cambiarPendiente(tienda, pedidoId, new Update()
                .set("estado", PedidoTienda.LISTO_PARA_RETIRAR)
                .set("fechaListoRetiro", LocalDateTime.now()));
        avisos.pedidoListoParaRetirar(pedido);
        return pedido;
    }

    /**
     * La tienda valida el código que le muestra el cliente al retirar. Un solo uso: el pedido pasa
     * a RETIRADO con una escritura condicional que además exige que el código siga siendo el
     * vigente, y la línea de la tienda queda habilitada para liquidar. Solo la tienda dueña del
     * pedido puede validar. Con {@value #MAX_INTENTOS_CODIGO} códigos erróneos seguidos el pedido
     * se bloquea {@value #BLOQUEO_CODIGO_MINUTOS} minutos (el código es corto: se limita el adivinar).
     */
    public PedidoTienda validarRetiro(String correoTienda, String pedidoId, String codigo) {
        Tienda tienda = tiendaDe(correoTienda);
        PedidoTienda p = pedidoRepository.findById(pedidoId)
                .filter(x -> x.esCatalogo() && tienda.getId().equals(x.getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!PedidoTienda.RETIRO.equals(p.getEntrega())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido es con envío: no se retira con código");
        }
        if (PedidoTienda.RETIRADO.equals(p.getEstado()) || Boolean.TRUE.equals(p.getCodigoRetiroUsado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya fue retirado");
        }
        if (!PedidoTienda.LISTO_PARA_RETIRAR.equals(p.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero marca el pedido como listo para retirar");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (p.getCodigoRetiroBloqueadoHasta() != null && ahora.isBefore(p.getCodigoRetiroBloqueadoHasta())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos con un código incorrecto. Vuelve a intentar en unos minutos.");
        }
        if (!CodigoRetiro.coincide(p.getCodigoRetiroHash(), pedidoId, codigo)) {
            PedidoTienda t = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("id").is(pedidoId)),
                    new Update().inc("codigoRetiroIntentos", 1),
                    FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
            int intentos = t != null && t.getCodigoRetiroIntentos() != null ? t.getCodigoRetiroIntentos() : 1;
            if (intentos >= MAX_INTENTOS_CODIGO) {
                mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(pedidoId)),
                        new Update().set("codigoRetiroIntentos", 0)
                                .set("codigoRetiroBloqueadoHasta", ahora.plusMinutes(BLOQUEO_CODIGO_MINUTOS)),
                        PedidoTienda.class);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Código incorrecto. Por seguridad el pedido se bloquea " + BLOQUEO_CODIGO_MINUTOS + " minutos.");
            }
            int restantes = MAX_INTENTOS_CODIGO - intentos;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código no es correcto. Te quedan " + restantes
                    + (restantes == 1 ? " intento" : " intentos") + " antes de que el pedido se bloquee "
                    + BLOQUEO_CODIGO_MINUTOS + " minutos.");
        }
        PedidoTienda retirado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("tiendaId").is(tienda.getId())
                        .and("estado").is(PedidoTienda.LISTO_PARA_RETIRAR)
                        .and("codigoRetiroUsado").ne(true)
                        .and("codigoRetiroHash").is(p.getCodigoRetiroHash())),
                new Update().set("estado", PedidoTienda.RETIRADO).set("codigoRetiroUsado", true)
                        .set("fechaRetirado", ahora).set("codigoRetiroIntentos", 0),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (retirado == null) {
            // Otro clic ganó, o el cliente generó un código nuevo mientras tanto
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido cambió: revisa su estado y vuelve a intentar");
        }
        // La tienda ya entregó: su línea del reparto queda habilitada para liquidar
        liquidacion.pedidoRecibido(pedidoId, "RETIRO_VALIDADO_POR_TIENDA");
        return retirado;
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

    /** Los pedidos de una compra directa no tienen orden de servicio. */
    private java.util.Optional<OrdenServicio> ordenDe(PedidoTienda pedido) {
        return pedido.getOrdenId() == null ? java.util.Optional.empty() : ordenRepository.findById(pedido.getOrdenId());
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
