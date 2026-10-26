package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CotizacionDTO;
import com.drivemaster.drivemaster.dto.CrearCotizacionRequest;
import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.RespuestaCotizacionRequest;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import com.drivemaster.drivemaster.util.CalculadoraCotizacion;
import com.drivemaster.drivemaster.util.IvaTaller;
import com.drivemaster.drivemaster.util.PdfCotizacionBuilder;
import com.drivemaster.drivemaster.util.TokenSeguro;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Cotizaciones del marketplace: el taller la emite desde una orden DIAGNOSTICADA y
 * el cliente la aprueba, la rechaza o pide cambios (con sesión o con el enlace del
 * correo).
 *
 * Reglas de dinero (aprobadas en la Fase 3):
 *  - repuestos al precio de la tienda en el momento de emitir (congelado);
 *  - IVA con el parámetro IVA sobre repuestos y mano de obra;
 *  - el diagnóstico pagado se descuenta del total, con tope en la mano de obra con IVA;
 *  - la comisión es informativa (no se cobra al cliente).
 *
 * El token del correo nunca se guarda en claro ni se escribe en el log: solo su hash.
 */
@Service
public class CotizacionService {

    private static final Logger log = LoggerFactory.getLogger(CotizacionService.class);

    static final String ENLACE_INVALIDO = "Enlace no válido o vencido";
    private static final int MAX_ITEMS_MANO_OBRA = 20;
    private static final int MAX_TEXTO = 1000;
    private static final double MAX_VALOR = 100_000_000;
    private static final double MAX_HORAS = 200;

    private final CotizacionRepository cotizacionRepository;
    private final OrdenServicioRepository ordenRepository;
    private final MongoTemplate mongoTemplate;
    private final TransicionOrdenService transiciones;
    private final TallerService tallerService;
    private final UsuarioService usuarioService;
    private final VehiculoService vehiculoService;
    private final ProductoService productoService;
    private final BuscadorRepuestosService buscador;
    private final ParametrosMarketplace parametros;
    private final AvisosSolicitudServicio avisos;
    private final LiquidacionService liquidacion;
    private final PredictorCotizacionService predictor;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                             OrdenServicioRepository ordenRepository,
                             MongoTemplate mongoTemplate,
                             TransicionOrdenService transiciones,
                             TallerService tallerService,
                             UsuarioService usuarioService,
                             VehiculoService vehiculoService,
                             ProductoService productoService,
                             BuscadorRepuestosService buscador,
                             ParametrosMarketplace parametros,
                             AvisosSolicitudServicio avisos,
                             LiquidacionService liquidacion,
                             PredictorCotizacionService predictor) {
        this.liquidacion = liquidacion;
        this.predictor = predictor;
        this.cotizacionRepository = cotizacionRepository;
        this.ordenRepository = ordenRepository;
        this.mongoTemplate = mongoTemplate;
        this.transiciones = transiciones;
        this.tallerService = tallerService;
        this.usuarioService = usuarioService;
        this.vehiculoService = vehiculoService;
        this.productoService = productoService;
        this.buscador = buscador;
        this.parametros = parametros;
        this.avisos = avisos;
    }

    // ─── Taller ──────────────────────────────────────────────────────────────

    /** Calcula la cotización sin guardarla (para que el taller revise los totales) y genera predicción de aprobación con ML. */
    public CotizacionDTO previsualizar(String correoTaller, String ordenId, CrearCotizacionRequest r) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = ordenCotizable(taller, ordenId);
        Cotizacion cotizacion = armar(taller, orden, r);
        CotizacionDTO dto = CotizacionDTO.completa(cotizacion);
        dto.setPrediccionAprobacion(calcularPrediccion(taller, orden, cotizacion));
        return dto;
    }

    private com.drivemaster.drivemaster.dto.PrediccionAprobacionDTO calcularPrediccion(Taller taller, OrdenServicio orden, Cotizacion cot) {
        try {
            Vehiculo vehiculo = orden.getVehiculoId() != null ? vehiculoService.obtenerEntidad(orden.getVehiculoId()) : null;

            java.util.Map<String, Object> p = new java.util.HashMap<>();
            p.put("monto_repuestos", cot.getSubtotalRepuestos() != null ? cot.getSubtotalRepuestos() : 0.0);
            p.put("monto_mano_obra", cot.getSubtotalManoObra() != null ? cot.getSubtotalManoObra() : 0.0);
            p.put("total_con_iva", cot.getTotal() != null ? cot.getTotal() : 0.0);

            long cantRepuestos = cot.getItems() != null ? cot.getItems().stream().filter(i -> CotizacionItem.REPUESTO.equals(i.getTipo())).count() : 0;
            long numTiendas = cot.getItems() != null ? cot.getItems().stream().filter(i -> CotizacionItem.REPUESTO.equals(i.getTipo()) && i.getTiendaId() != null).map(CotizacionItem::getTiendaId).distinct().count() : 0;

            p.put("cantidad_repuestos", cantRepuestos);
            p.put("num_tiendas", numTiendas);
            p.put("tipo_servicio", orden.getProductoNombre() != null ? orden.getProductoNombre() : "MANTENIMIENTO_PREVENTIVO");
            p.put("marca_vehiculo", vehiculo != null && vehiculo.getMarca() != null ? vehiculo.getMarca() : "Chevrolet");
            p.put("anio_vehiculo", vehiculo != null && vehiculo.getAnio() != null ? vehiculo.getAnio() : 2018);
            p.put("distancia_km", 5.0); // distancia estimada

            // Tiempo de respuesta: horas desde la creación de la orden hasta hoy
            double horas = 2.0;
            if (orden.getFechaCreacion() != null) {
                horas = Math.max(0.5, java.time.Duration.between(orden.getFechaCreacion(), LocalDateTime.now()).toMinutes() / 60.0);
            }
            p.put("tiempo_respuesta_horas", Math.round(horas * 10.0) / 10.0);
            p.put("calificacion_taller", taller.getRating() != null ? taller.getRating() : 4.6);

            return predictor.predecir(p);
        } catch (Exception e) {
            log.warn("No se pudo calcular predicción de cotización: {}", e.getMessage());
            return null;
        }
    }

    /** Emite la cotización: la guarda, pasa la orden a COTIZADA y envía el correo al cliente. */
    public CotizacionDTO emitir(String correoTaller, String ordenId, CrearCotizacionRequest r) {
        Taller taller = tallerOperativo(correoTaller);
        OrdenServicio orden = ordenCotizable(taller, ordenId);
        Cotizacion cotizacion = armar(taller, orden, r);

        Cotizacion anterior = cotizacionRepository.findFirstByOrdenIdOrderByVersionDesc(orden.getId()).orElse(null);
        TokenSeguro.Generado token = TokenSeguro.generar();
        cotizacion.setVersion(anterior != null && anterior.getVersion() != null ? anterior.getVersion() + 1 : 1);
        cotizacion.setEstado(Cotizacion.EMITIDA);
        cotizacion.setTokenHash(token.hash());
        cotizacion.setTokenUsado(false);
        cotizacion.setFechaEmision(LocalDateTime.now());
        cotizacion.setEmitidaPor(taller.getNombre());
        cotizacion = cotizacionRepository.save(cotizacion);

        OrdenServicio cotizada;
        try {
            cotizada = transiciones.transicionar(orden, EstadoOrden.COTIZADA, actorTaller(taller),
                    "Cotización v" + cotizacion.getVersion() + " emitida", Criteria.where("tallerId").is(taller.getId()),
                    new Update().set("cotizacionId", cotizacion.getId()));
        } catch (RuntimeException e) {
            cotizacionRepository.delete(cotizacion);
            throw e;
        }
        // Las versiones anteriores que siguieran abiertas dejan de valer
        mongoTemplate.updateMulti(Query.query(Criteria.where("ordenId").is(orden.getId())
                        .and("estado").is(Cotizacion.EMITIDA).and("id").ne(cotizacion.getId())),
                new Update().set("estado", Cotizacion.REEMPLAZADA).set("tokenUsado", true), Cotizacion.class);

        avisos.cotizacionEmitida(cotizada, cotizacion, token.token());
        return CotizacionDTO.completa(cotizacion);
    }

    // ─── Consulta con sesión ─────────────────────────────────────────────────

    /** Cotizaciones de una orden, la más reciente primero. Cliente dueño, taller asignado o ADMIN. */
    public List<CotizacionDTO> deOrden(String correo, String ordenId) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);
        OrdenServicio orden = obtenerMarketplace(ordenId);
        String actor = EstadoOrden.actorDeRol(usuario.getRol());
        boolean acceso = EstadoOrden.ADMIN.equals(actor)
                || (EstadoOrden.CLIENTE.equals(actor) && usuario.getId().equals(orden.getUsuarioId()))
                || (EstadoOrden.TALLER.equals(actor)
                    && tallerService.obtenerPorUsuario(correo).getId().equals(orden.getTallerId()));
        if (!acceso) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a esta solicitud");
        }
        return cotizacionRepository.findByOrdenIdOrderByVersionDesc(orden.getId()).stream()
                .filter(c -> !EstadoOrden.TALLER.equals(actor) || orden.getTallerId().equals(c.getTallerId()))
                .map(CotizacionDTO::completa)
                .toList();
    }

    /**
     * PDF de una cotización. Solo el cliente dueño de la cotización o el taller que la emitió; nadie más, ni otro
     * taller ni el ADMIN. Cualquier otro recibe 404, para no revelar que la cotización existe.
     */
    public byte[] pdf(String correo, String cotizacionId) {
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);
        String actor = EstadoOrden.actorDeRol(usuario.getRol());
        Taller taller = null;
        boolean acceso;
        if (EstadoOrden.CLIENTE.equals(actor)) {
            acceso = usuario.getId().equals(cotizacion.getUsuarioId());
        } else if (EstadoOrden.TALLER.equals(actor)) {
            taller = tallerService.obtenerPorUsuario(correo);
            acceso = taller.getId().equals(cotizacion.getTallerId());
        } else {
            acceso = false;
        }
        if (!acceso) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización no encontrada");
        }
        if (taller == null) taller = tallerDe(cotizacion);
        return PdfCotizacionBuilder.construir(cotizacion, taller);
    }

    /** Taller de la cotización para los datos del PDF; si ya no existe, el PDF usa el nombre guardado en la cotización. */
    private Taller tallerDe(Cotizacion cotizacion) {
        try {
            return cotizacion.getTallerId() != null ? tallerService.obtenerEntidad(cotizacion.getTallerId()) : null;
        } catch (ResponseStatusException e) {
            return null;
        }
    }

    /** Todas las cotizaciones del taller de la sesión, la más reciente primero. El taller sale de la sesión, nunca de un parámetro. */
    public List<CotizacionDTO> delTaller(String correoTaller) {
        String tallerId = tallerService.obtenerPorUsuario(correoTaller).getId();
        return cotizacionRepository.findByTallerIdOrderByFechaEmisionDesc(tallerId).stream()
                .map(CotizacionDTO::completa)
                .toList();
    }

    /** Respuesta del cliente desde su panel. */
    public CotizacionDTO responder(String correoCliente, String cotizacionId, RespuestaCotizacionRequest r) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));
        if (!cliente.getId().equals(cotizacion.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta cotización no le pertenece");
        }
        if (!CotizacionDTO.respondible(cotizacion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cotización ya no se puede responder");
        }
        return CotizacionDTO.completa(aplicarRespuesta(cotizacion, r, false));
    }

    // ─── Enlace del correo (sin sesión) ──────────────────────────────────────

    /** Detalle de la cotización del enlace. Error genérico si el token no sirve. */
    public CotizacionDTO consultarPublica(String token) {
        return CotizacionDTO.publica(porToken(token));
    }

    public CotizacionDTO responderPublica(RespuestaCotizacionRequest r) {
        Cotizacion cotizacion = porToken(r.getToken());
        if (!CotizacionDTO.respondible(cotizacion)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ENLACE_INVALIDO);
        }
        return CotizacionDTO.publica(aplicarRespuesta(cotizacion, r, true));
    }

    // ─── Vencimientos ────────────────────────────────────────────────────────

    /**
     * Cotizaciones emitidas que pasaron su vigencia → VENCIDA y la orden vuelve a
     * DIAGNOSTICADA (el taller puede emitir otra con precios actualizados).
     * Devuelve cuántas venció.
     */
    public int vencerCotizaciones() {
        int vencidas = 0;
        for (Cotizacion c : cotizacionRepository.findByEstadoAndVigenciaHastaBefore(Cotizacion.EMITIDA, LocalDateTime.now())) {
            try {
                Cotizacion marcada = mongoTemplate.findAndModify(
                        Query.query(Criteria.where("id").is(c.getId()).and("estado").is(Cotizacion.EMITIDA)),
                        new Update().set("estado", Cotizacion.VENCIDA).set("tokenUsado", true),
                        FindAndModifyOptions.options().returnNew(true), Cotizacion.class);
                if (marcada == null) continue; // el cliente respondió justo ahora
                ordenRepository.findById(c.getOrdenId())
                        .filter(o -> EstadoOrden.COTIZADA.equals(o.getEstado()) && c.getId().equals(o.getCotizacionId()))
                        .ifPresent(o -> transiciones.transicionar(o, EstadoOrden.DIAGNOSTICADA, Actor.SISTEMA,
                                "La cotización v" + c.getVersion() + " venció sin respuesta",
                                Criteria.where("cotizacionId").is(c.getId()), null));
                avisos.cotizacionVencida(marcada);
                vencidas++;
            } catch (ResponseStatusException e) {
                log.debug("Cotización {}: la orden ya cambió, se omite", c.getId());
            } catch (Exception e) {
                log.error("No se pudo vencer la cotización {}", c.getId(), e);
            }
        }

        // Aprobadas que no se pagaron dentro de la vigencia
        LocalDateTime ahora = LocalDateTime.now();
        for (OrdenServicio o : ordenRepository.findByFlujoAndEstadoOrderByFechaCreacionDesc(
                EstadoOrden.FLUJO_MARKETPLACE, EstadoOrden.APROBADA)) {
            try {
                Cotizacion c = o.getCotizacionId() != null ? cotizacionRepository.findById(o.getCotizacionId()).orElse(null) : null;
                if (c == null || c.getVigenciaHasta() == null || c.getVigenciaHasta().isAfter(ahora)) continue;
                if (devolverAprobadaADiagnosticada(o, c, Cotizacion.VENCIDA,
                        "La cotización v" + c.getVersion() + " venció sin pago")) {
                    avisos.cotizacionVencida(c);
                    vencidas++;
                }
            } catch (ResponseStatusException e) {
                log.debug("Orden {}: ya cambió, se omite", o.getId());
            } catch (Exception e) {
                log.error("No se pudo vencer la cotización aprobada de la orden {}", o.getId(), e);
            }
        }
        return vencidas;
    }

    /**
     * Cotización aprobada que ya no se puede pagar (venció o falta stock): la marca
     * con el estado indicado y devuelve la orden APROBADA → COTIZADA → DIAGNOSTICADA
     * para que el taller emita otra. Devuelve false si alguien la cambió antes.
     */
    public boolean devolverAprobadaADiagnosticada(OrdenServicio orden, Cotizacion c, String estadoCotizacion, String nota) {
        Cotizacion marcada = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(c.getId()).and("estado").is(Cotizacion.APROBADA)),
                new Update().set("estado", estadoCotizacion).set("motivoRespuesta", nota),
                FindAndModifyOptions.options().returnNew(true), Cotizacion.class);
        if (marcada == null) return false;
        Criteria mismaCotizacion = Criteria.where("cotizacionId").is(c.getId());
        OrdenServicio cotizada = transiciones.transicionar(orden, EstadoOrden.COTIZADA, Actor.SISTEMA, nota, mismaCotizacion, null);
        transiciones.transicionar(cotizada, EstadoOrden.DIAGNOSTICADA, Actor.SISTEMA,
                "El taller debe emitir una nueva cotización", mismaCotizacion, null);
        return true;
    }

    // ─── Respuesta ───────────────────────────────────────────────────────────

    /**
     * Aplica la respuesta en dos escrituras condicionales: primero marca la
     * cotización (solo si sigue EMITIDA, vigente y con el token sin usar: un solo
     * uso aunque lleguen dos clics a la vez) y luego mueve la orden. Si la orden ya
     * no está en COTIZADA con esta cotización, se deshace la marca.
     */
    private Cotizacion aplicarRespuesta(Cotizacion cotizacion, RespuestaCotizacionRequest r, boolean publica) {
        String accion = r.getAccion() != null ? r.getAccion().trim().toUpperCase() : "";
        String motivo = r.getMotivo() != null ? r.getMotivo().trim() : "";
        if (motivo.length() > MAX_TEXTO) motivo = motivo.substring(0, MAX_TEXTO);

        String estadoCotizacion;
        String estadoOrden;
        String respuesta;
        switch (accion) {
            case RespuestaCotizacionRequest.APROBAR -> {
                estadoCotizacion = Cotizacion.APROBADA;
                estadoOrden = EstadoOrden.APROBADA;
                respuesta = "aprobada";
            }
            case RespuestaCotizacionRequest.RECHAZAR -> {
                estadoCotizacion = Cotizacion.RECHAZADA;
                estadoOrden = EstadoOrden.CANCELADA;
                respuesta = "rechazada";
            }
            case RespuestaCotizacionRequest.CAMBIOS -> {
                if (motivo.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuéntale al taller qué quieres cambiar");
                }
                estadoCotizacion = Cotizacion.CAMBIOS_SOLICITADOS;
                estadoOrden = EstadoOrden.DIAGNOSTICADA;
                respuesta = "cambios solicitados";
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción no válida");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Cotizacion marcada = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(cotizacion.getId())
                        .and("estado").is(Cotizacion.EMITIDA)
                        .and("tokenUsado").ne(true)
                        .and("vigenciaHasta").gt(ahora)),
                new Update().set("estado", estadoCotizacion).set("tokenUsado", true)
                        .set("fechaRespuesta", ahora).set("motivoRespuesta", motivo.isBlank() ? null : motivo),
                FindAndModifyOptions.options().returnNew(true), Cotizacion.class);
        if (marcada == null) {
            throw publica ? new ResponseStatusException(HttpStatus.NOT_FOUND, ENLACE_INVALIDO)
                    : new ResponseStatusException(HttpStatus.CONFLICT, "Esta cotización ya no se puede responder");
        }

        try {
            OrdenServicio orden = ordenRepository.findById(cotizacion.getOrdenId())
                    .filter(o -> EstadoOrden.COTIZADA.equals(o.getEstado()) && cotizacion.getId().equals(o.getCotizacionId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya cambió"));
            Usuario cliente = usuarioService.obtenerPorId(orden.getUsuarioId());
            Actor actor = new Actor(EstadoOrden.CLIENTE, cliente.getId(), cliente.getNombre());
            String nota = "Cotización v" + cotizacion.getVersion() + " " + respuesta + (publica ? " desde el correo" : "")
                    + (motivo.isBlank() ? "" : ": " + motivo);
            Update cambios = EstadoOrden.CANCELADA.equals(estadoOrden)
                    ? new Update().set("motivoCierre", "El cliente rechazó la cotización") : null;
            transiciones.transicionar(orden, estadoOrden, actor, nota,
                    Criteria.where("cotizacionId").is(cotizacion.getId()), cambios);
        } catch (RuntimeException e) {
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(cotizacion.getId()).and("estado").is(estadoCotizacion)),
                    new Update().set("estado", Cotizacion.EMITIDA).set("tokenUsado", false)
                            .unset("fechaRespuesta").unset("motivoRespuesta"), Cotizacion.class);
            if (publica) throw new ResponseStatusException(HttpStatus.NOT_FOUND, ENLACE_INVALIDO);
            throw e;
        }

        // El cliente rechazó tras el diagnóstico: el taller ya hizo su trabajo, su línea queda habilitada
        if (RespuestaCotizacionRequest.RECHAZAR.equals(accion)) {
            liquidacion.ordenCanceladaPorRechazoDeCotizacion(cotizacion.getOrdenId());
        }
        avisos.cotizacionRespondida(marcada, respuesta, motivo);
        return marcada;
    }

    private Cotizacion porToken(String token) {
        if (!TokenSeguro.formatoValido(token)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ENLACE_INVALIDO);
        }
        return cotizacionRepository.findByTokenHash(TokenSeguro.hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ENLACE_INVALIDO));
    }

    // ─── Armado ──────────────────────────────────────────────────────────────

    /**
     * Construye la cotización: repuestos de la selección de la orden (revalidados con
     * el buscador y con el precio actual de la tienda) y la mano de obra del pedido.
     */
    private Cotizacion armar(Taller taller, OrdenServicio orden, CrearCotizacionRequest r) {
        List<CotizacionItem> items = new ArrayList<>();
        int diasEntrega = 0;

        List<RepuestoSeleccionado> repuestos = orden.getRepuestos() != null ? orden.getRepuestos() : List.of();
        if (!repuestos.isEmpty()) {
            Vehiculo vehiculo = vehiculoService.obtenerEntidad(orden.getVehiculoId());
            for (RepuestoSeleccionado rep : repuestos) {
                Producto producto;
                try {
                    producto = productoService.obtenerPorId(rep.getProductoId());
                } catch (RuntimeException e) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "\"" + rep.getProductoNombre() + "\" ya no existe: vuelve a elegir los repuestos");
                }
                int cantidad = rep.getCantidad() != null ? rep.getCantidad() : 1;
                OfertaRepuestoDTO oferta = buscador.ofertaParaSeleccion(producto, cantidad, taller, vehiculo);
                if (oferta.isRequiereConfirmacion() && !Boolean.TRUE.equals(rep.getCompatibilidadConfirmada())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Falta confirmar la compatibilidad de \"" + producto.getNombre() + "\": vuelve a elegir los repuestos");
                }
                if (oferta.getPrecio() == null || oferta.getPrecio() <= 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + producto.getNombre() + "\" no tiene precio");
                }
                if (OfertaRepuestoDTO.ENCARGO.equals(oferta.getDisponibilidad()) && oferta.getDiasEntrega() != null) {
                    diasEntrega = Math.max(diasEntrega, oferta.getDiasEntrega());
                }
                items.add(CotizacionItem.builder()
                        .tipo(CotizacionItem.REPUESTO)
                        .descripcion(rep.getDescripcion() != null ? rep.getDescripcion() : producto.getNombre())
                        .productoId(producto.getId())
                        .productoNombre(producto.getNombre())
                        .marca(producto.getMarca())
                        .referencia(producto.getReferencia())
                        .tiendaId(oferta.getTiendaId())
                        .tiendaNombre(oferta.getTiendaNombre())
                        .disponibilidad(oferta.getDisponibilidad())
                        .diasEntrega(oferta.getDiasEntrega())
                        .cantidad(cantidad)
                        .precioUnitario(oferta.getPrecio())
                        .build());
            }
        }

        List<CrearCotizacionRequest.ManoObra> manoObra = r != null && r.getManoObra() != null ? r.getManoObra() : List.of();
        if (manoObra.size() > MAX_ITEMS_MANO_OBRA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_ITEMS_MANO_OBRA + " líneas de mano de obra");
        }
        Double tarifaHora = taller.getTarifaHora();
        for (CrearCotizacionRequest.ManoObra m : manoObra) {
            String descripcion = m.getDescripcion() != null ? m.getDescripcion().trim() : "";
            if (descripcion.isBlank() || descripcion.length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe cada trabajo de mano de obra (máx. 200 caracteres)");
            }
            if (CotizacionItem.POR_HORAS.equalsIgnoreCase(m.getModalidad())) {
                double horas = m.getHoras() != null ? m.getHoras() : 0;
                if (horas <= 0 || horas > MAX_HORAS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horas no válidas en \"" + descripcion + "\"");
                }
                if (tarifaHora == null || tarifaHora <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Configura la tarifa por hora de tu taller o cotiza la mano de obra por valor fijo");
                }
                items.add(CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).descripcion(descripcion)
                        .modalidad(CotizacionItem.POR_HORAS).horas(horas).precioUnitario(tarifaHora).build());
            } else {
                double valor = m.getValor() != null ? m.getValor() : 0;
                if (valor <= 0 || valor > MAX_VALOR) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor no válido en \"" + descripcion + "\"");
                }
                items.add(CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).descripcion(descripcion)
                        .modalidad(CotizacionItem.POR_ITEM).cantidad(1).precioUnitario(valor).build());
            }
        }
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cotización debe tener repuestos o mano de obra");
        }

        Integer tiempo = r != null ? r.getTiempoEstimadoHoras() : null;
        if (tiempo != null && (tiempo < 1 || tiempo > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiempo estimado no válido");
        }
        String observaciones = r != null && r.getObservaciones() != null ? r.getObservaciones().trim() : null;
        if (observaciones != null && observaciones.length() > MAX_TEXTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Observaciones demasiado largas (máx. " + MAX_TEXTO + ")");
        }

        // El anticipo solo existe si el cliente pagó el diagnóstico
        double anticipo = EstadoOrden.PAGO_PAGADO.equals(orden.getPagoDiagnosticoEstado()) && orden.getTarifaDiagnostico() != null
                ? orden.getTarifaDiagnostico() : 0;
        // Repuestos con el IVA global; mano de obra con el del taller (0 % si no es responsable de IVA)
        double ivaRepuestosPct = parametros.ivaPct();
        double ivaManoObraPct = IvaTaller.pct(taller, ivaRepuestosPct);
        double comisionPct = parametros.comisionPct();
        CalculadoraCotizacion.Totales t = CalculadoraCotizacion.calcular(items, ivaRepuestosPct, ivaManoObraPct, anticipo, comisionPct);

        return Cotizacion.builder()
                .ordenId(orden.getId())
                .tallerId(taller.getId())
                .tallerNombre(taller.getNombre())
                .usuarioId(orden.getUsuarioId())
                .vehiculoDescripcion(orden.getVehiculoDescripcion())
                .items(items)
                .subtotalRepuestos(t.subtotalRepuestos())
                .subtotalManoObra(t.subtotalManoObra())
                .subtotal(t.subtotal())
                // El porcentaje único solo si los dos coinciden: así nadie lo lee como el IVA de toda la cotización
                .ivaPct(ivaRepuestosPct == ivaManoObraPct ? ivaRepuestosPct : null)
                .ivaRepuestosPct(ivaRepuestosPct)
                .ivaManoObraPct(ivaManoObraPct)
                .ivaRepuestos(t.ivaRepuestos())
                .ivaManoObra(t.ivaManoObra())
                .iva(t.iva())
                .total(t.total())
                .anticipoDiagnostico(t.anticipo())
                .totalAPagar(t.totalAPagar())
                .comisionPct(comisionPct)
                .comisionInformativa(t.comision())
                .vigenciaHasta(LocalDateTime.now().plusDays(parametros.vigenciaCotizacionDias()))
                .tiempoEstimadoHoras(tiempo)
                .diasEntregaRepuestos(diasEntrega > 0 ? diasEntrega : null)
                .observaciones(observaciones != null && !observaciones.isBlank() ? observaciones : null)
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private OrdenServicio ordenCotizable(Taller taller, String ordenId) {
        OrdenServicio orden = obtenerMarketplace(ordenId);
        if (!taller.getId().equals(orden.getTallerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no está asignada a tu taller");
        }
        if (!EstadoOrden.DIAGNOSTICADA.equals(orden.getEstado()) || orden.getDiagnostico() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se cotiza una solicitud diagnosticada");
        }
        return orden;
    }

    private OrdenServicio obtenerMarketplace(String id) {
        OrdenServicio orden = ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!EstadoOrden.FLUJO_MARKETPLACE.equals(orden.getFlujo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        return orden;
    }

    private Taller tallerOperativo(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion()) || !Boolean.TRUE.equals(taller.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller no está habilitado para operar");
        }
        return taller;
    }

    private static Actor actorTaller(Taller taller) {
        return new Actor(EstadoOrden.TALLER, taller.getId(), taller.getNombre());
    }
}
