package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.catalogo.CompraCotizadaDTO;
import com.drivemaster.drivemaster.dto.catalogo.CompraDTO;
import com.drivemaster.drivemaster.dto.catalogo.CompraRequest;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CompraCatalogoRepository;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.util.CodigoRetiro;
import com.drivemaster.drivemaster.util.CompatibilidadVehiculo;
import com.drivemaster.drivemaster.util.Dinero;
import org.bson.types.ObjectId;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compra directa del catálogo: un carrito con repuestos de varias tiendas, UN solo pago de
 * Wompi y un pedido por tienda. No pasa por ningún taller.
 *
 * Reglas:
 *  - Los precios, el IVA y el costo de envío se calculan aquí; el navegador solo dice qué y cuánto.
 *  - No se reserva stock en el carrito: se descuenta al pagar. Antes de generar el link se revisa
 *    el stock de TODAS las tiendas (si falta algo no se cobra nada).
 *  - Si una tienda se queda sin stock después de pagar: todo o nada. Se devuelve el stock ya
 *    descontado, la compra queda NO_APLICADA y se registra el reembolso completo.
 *  - El pago se reparte a la tienda dueña de cada producto (RepartoPago por pedido), con el IVA y
 *    el envío de su pedido; DriveMaster no retiene nada en v1 (la comisión es informativa).
 *  - La liquidación sigue la misma regla por evento de la 4-B (ver LiquidacionService).
 *
 * La confirmación entra por PagoMarketplaceService.confirmar (ya verificada con Wompi y tomada
 * de forma atómica): aquí solo se aplican sus efectos.
 */
@Service
public class CompraCatalogoService {

    private static final Logger log = LoggerFactory.getLogger(CompraCatalogoService.class);

    static final int MAX_LINEAS = 40;
    static final int MAX_CANTIDAD = 99;
    private static final String SALIDA = "SALIDA";
    private static final String ENTRADA = "ENTRADA";

    private final CatalogoService catalogo;
    private final MongoTemplate mongoTemplate;
    private final CompraCatalogoRepository compraRepository;
    private final PedidoTiendaRepository pedidoRepository;
    private final RepartoPagoRepository repartoRepository;
    private final ReembolsoRepository reembolsoRepository;
    private final PagoRepository pagoRepository;
    private final PagoService pagoService;
    private final UsuarioService usuarioService;
    private final VehiculoService vehiculoService;
    private final ProductoService productoService;
    private final MovimientoInventarioService movimientoService;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ParametrosMarketplace parametros;
    private final AvisosSolicitudServicio avisos;

    public CompraCatalogoService(CatalogoService catalogo,
                                 MongoTemplate mongoTemplate,
                                 CompraCatalogoRepository compraRepository,
                                 PedidoTiendaRepository pedidoRepository,
                                 RepartoPagoRepository repartoRepository,
                                 ReembolsoRepository reembolsoRepository,
                                 PagoRepository pagoRepository,
                                 PagoService pagoService,
                                 UsuarioService usuarioService,
                                 VehiculoService vehiculoService,
                                 ProductoService productoService,
                                 MovimientoInventarioService movimientoService,
                                 MovimientoInventarioRepository movimientoRepository,
                                 ParametrosMarketplace parametros,
                                 AvisosSolicitudServicio avisos) {
        this.catalogo = catalogo;
        this.mongoTemplate = mongoTemplate;
        this.compraRepository = compraRepository;
        this.pedidoRepository = pedidoRepository;
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.pagoRepository = pagoRepository;
        this.pagoService = pagoService;
        this.usuarioService = usuarioService;
        this.vehiculoService = vehiculoService;
        this.productoService = productoService;
        this.movimientoService = movimientoService;
        this.movimientoRepository = movimientoRepository;
        this.parametros = parametros;
        this.avisos = avisos;
    }

    // ─── Cotizar (sin efectos) ───────────────────────────────────────────────

    public CompraCotizadaDTO cotizar(String correoCliente, CompraRequest r) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        Calculo c = calcular(cliente, r);
        return new CompraCotizadaDTO(c.lineas, c.entregas, c.subtotal.doubleValue(), c.iva.doubleValue(),
                c.envio.doubleValue(), c.total.doubleValue(), c.avisos);
    }

    // ─── Pagar ───────────────────────────────────────────────────────────────

    /**
     * Revisa el stock de TODAS las tiendas, guarda la compra (PENDIENTE_PAGO) y genera el link
     * de Wompi por el total. No descuenta nada: eso ocurre al confirmarse el pago.
     */
    public Map<String, Object> iniciarPago(String correoCliente, CompraRequest r) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        Calculo c = calcular(cliente, r);

        CompraCatalogo compra = compraRepository.save(CompraCatalogo.builder()
                .clienteId(cliente.getId())
                .clienteNombre(cliente.getNombre())
                .clienteCorreo(cliente.getCorreo())
                .lineas(c.lineas)
                .entregas(c.entregas)
                .envioReceptor(hayEnvio(c.entregas) ? texto(r.envio().receptor()) : null)
                .envioTelefono(hayEnvio(c.entregas) ? texto(r.envio().telefono()) : null)
                .envioDireccion(hayEnvio(c.entregas) ? texto(r.envio().direccion()) : null)
                .envioCiudad(hayEnvio(c.entregas) ? texto(r.envio().ciudad()) : null)
                .envioIndicaciones(hayEnvio(c.entregas) ? texto(r.envio().indicaciones()) : null)
                .subtotal(c.subtotal.doubleValue())
                .iva(c.iva.doubleValue())
                .costoEnvio(c.envio.doubleValue())
                .total(c.total.doubleValue())
                .estado(CompraCatalogo.PENDIENTE_PAGO)
                .fechaCreacion(LocalDateTime.now())
                .build());
        try {
            Map<String, Object> link = pagoService.crearPagoCatalogo(cliente.getId(), compra.getTotal(), compra.getId());
            String referencia = String.valueOf(link.get("referencia"));
            compra.setReferenciaPago(referencia);
            pagoRepository.findByReferencia(referencia).ifPresent(p -> compra.setPagoId(p.getId()));
            compraRepository.save(compra);
            Map<String, Object> respuesta = new LinkedHashMap<>(link);
            respuesta.put("compraId", compra.getId());
            return respuesta;
        } catch (RuntimeException e) {
            compraRepository.deleteById(compra.getId());
            throw e;
        }
    }

    // ─── Aplicar el pago confirmado ──────────────────────────────────────────

    /**
     * Efectos de un pago CATALOGO ya confirmado: descuenta el stock de todas las tiendas, crea un
     * pedido por tienda con su reparto y avisa. Todo o nada: si algo falla, deja la compra
     * NO_APLICADA con el reembolso completo. Nunca lanza.
     */
    void aplicar(PagoEntity pago) {
        CompraCatalogo compra = pago.getCompraCatalogoId() != null
                ? compraRepository.findById(pago.getCompraCatalogoId()).orElse(null) : null;
        if (compra == null) {
            log.error("Pago {} del catálogo sin compra {}", pago.getReferencia(), pago.getCompraCatalogoId());
            registrarReembolso(pago, null, pago.getMonto(), "No se encontró la compra de este pago");
            avisos.alertaAdmin("Pago de catálogo sin compra", "El pago " + pago.getReferencia() + " no tiene compra asociada.");
            return;
        }
        // Defensa extra: si este mismo pago ya se aplicó, no se hace nada (ni se reembolsa). La idempotencia real
        // la da PagoMarketplaceService.confirmar, que solo llega aquí una vez por pago.
        if (CompraCatalogo.PAGADA.equals(compra.getEstado()) && pago.getId() != null && pago.getId().equals(compra.getPagoId())) {
            log.info("Pago {} ya estaba aplicado a la compra {}: se omite", pago.getReferencia(), compra.getId());
            return;
        }
        if (!CompraCatalogo.PENDIENTE_PAGO.equals(compra.getEstado())) {
            noAplicada(pago, compra, "La compra ya no esperaba este pago (estado " + compra.getEstado() + ")");
            return;
        }
        if (Dinero.pesos(compra.getTotal()).compareTo(Dinero.pesos(pago.getMonto())) != 0) {
            noAplicada(pago, compra, "El monto pagado no coincide con el total de la compra");
            return;
        }

        String refInventario = PagoMarketplaceService.referenciaInventario(pago);
        List<CompraCatalogo.Linea> descontadas = new ArrayList<>();
        List<PedidoTienda> pedidos = new ArrayList<>();
        List<RepartoPago> repartos = new ArrayList<>();
        try {
            // 1. Stock de TODAS las tiendas (todo o nada)
            String faltante = descontarStock(pago, compra, refInventario, descontadas);
            if (faltante != null) {
                reponerStock(descontadas, refInventario);
                noAplicada(pago, compra, "Sin stock suficiente de \"" + faltante + "\" al confirmar el pago");
                return;
            }
            // 2. Un pedido por tienda, con su código de retiro y su reparto
            Map<String, String> codigos = new HashMap<>();
            for (CompraCatalogo.EntregaTienda entrega : compra.getEntregas()) {
                PedidoTienda pedido = crearPedido(pago, compra, entrega, codigos);
                pedidos.add(pedido);
                repartos.add(repartoRepository.save(reparto(pago, compra, pedido)));
            }
            compra.setPedidoIds(pedidos.stream().map(PedidoTienda::getId).toList());
            compra.setEstado(CompraCatalogo.PAGADA);
            compra.setFechaPago(pago.getFechaPago() != null ? pago.getFechaPago() : LocalDateTime.now());
            compra.setPagoId(pago.getId());
            compraRepository.save(compra);
            // 3. Comisión informativa (sobre los productos, antes de IVA y sin el envío) y avisos
            //    (un fallo de envío nunca deshace la compra)
            ComisionPlataforma.registrar(repartoRepository, pago, Dinero.pesos(compra.getSubtotal()), parametros.comisionPct());
            avisos.compraPagada(compra, pedidos, codigos, pago);
        } catch (RuntimeException e) {
            log.error("No se pudo aplicar el pago {} de la compra {}: se deshace todo", pago.getReferencia(), compra.getId(), e);
            deshacer(pedidos, repartos);
            reponerStock(descontadas, refInventario);
            noAplicada(pago, compra, "Error al completar la compra: se reembolsa el pago");
        }
    }

    /** Descuenta cada línea de stock con su SALIDA (idempotente). Devuelve el nombre del producto que falló, o null. */
    private String descontarStock(PagoEntity pago, CompraCatalogo compra, String refInventario,
                                  List<CompraCatalogo.Linea> descontadas) {
        Map<String, Tienda> visibles = catalogo.tiendasVisibles();
        for (CompraCatalogo.Linea l : compra.getLineas()) {
            Producto p = buscarProducto(l.getProductoId());
            // El producto o la tienda pudieron cambiar entre el link y el pago
            if (p == null || Boolean.FALSE.equals(p.getActivo()) || !visibles.containsKey(l.getTiendaId())
                    || Boolean.TRUE.equals(visibles.get(l.getTiendaId()).getPlataforma())) {
                return l.getProductoNombre();
            }
            if ("ENCARGO".equals(l.getTipo())) continue; // lo consigue la tienda; no hay stock que descontar
            if (movimientoRepository.existsByProductoIdAndTipoAndReferencia(l.getProductoId(), SALIDA, refInventario)) {
                log.warn("Pago {}: la salida de {} ya estaba registrada", pago.getReferencia(), l.getProductoId());
                descontadas.add(l);
                continue;
            }
            try {
                movimientoService.registrarMovimiento(l.getProductoId(), SALIDA, l.getCantidad(),
                        "Compra directa del catálogo", refInventario, pago.getUsuarioId());
                descontadas.add(l);
            } catch (StockInsuficienteException e) {
                return l.getProductoNombre();
            } catch (Exception e) {
                log.error("Pago {}: no se pudo descontar el stock de {}", pago.getReferencia(), l.getProductoId(), e);
                return l.getProductoNombre();
            }
        }
        return null;
    }

    private void reponerStock(List<CompraCatalogo.Linea> descontadas, String refInventario) {
        for (CompraCatalogo.Linea l : descontadas) {
            try {
                movimientoService.registrarMovimiento(l.getProductoId(), ENTRADA, l.getCantidad(),
                        "Reversa de compra del catálogo (pago no aplicado)", refInventario, null);
            } catch (Exception e) {
                log.error("No se pudo reponer el stock de {} ({} u.)", l.getProductoId(), l.getCantidad(), e);
            }
        }
    }

    private PedidoTienda crearPedido(PagoEntity pago, CompraCatalogo compra, CompraCatalogo.EntregaTienda entrega,
                                     Map<String, String> codigos) {
        Tienda tienda = catalogo.tiendasVisibles().get(entrega.getTiendaId());
        List<CompraCatalogo.Linea> lineas = compra.getLineas().stream()
                .filter(l -> l.getTiendaId().equals(entrega.getTiendaId())).toList();
        List<CotizacionItem> items = lineas.stream().map(l -> CotizacionItem.builder()
                .tipo(CotizacionItem.REPUESTO).productoId(l.getProductoId()).productoNombre(l.getProductoNombre())
                .marca(l.getMarca()).referencia(l.getReferencia()).tiendaId(l.getTiendaId()).tiendaNombre(l.getTiendaNombre())
                .disponibilidad("ENCARGO".equals(l.getTipo()) ? "ENCARGO" : "DISPONIBLE").diasEntrega(l.getDiasEntrega())
                .cantidad(l.getCantidad()).precioUnitario(l.getPrecioUnitario()).subtotal(l.getSubtotal()).build()).toList();
        Integer dias = items.stream().filter(i -> "ENCARGO".equals(i.getDisponibilidad()) && i.getDiasEntrega() != null)
                .map(CotizacionItem::getDiasEntrega).max(Integer::compare).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        boolean envio = PedidoTienda.ENVIO.equals(entrega.getTipo());

        String id = new ObjectId().toHexString();
        PedidoTienda.PedidoTiendaBuilder b = PedidoTienda.builder()
                .id(id)
                .origen(PedidoTienda.ORIGEN_CATALOGO)
                .compraId(compra.getId())
                .pagoId(pago.getId())
                .clienteId(compra.getClienteId())
                .clienteNombre(compra.getClienteNombre())
                .tiendaId(entrega.getTiendaId())
                .tiendaNombre(entrega.getTiendaNombre())
                .items(items)
                .subtotal(entrega.getSubtotal())
                .iva(entrega.getIva())
                .costoEnvio(entrega.getCostoEnvio())
                .total(entrega.getTotal())
                .diasEntrega(dias)
                .plazoDespachoHasta(PagoMarketplaceService.plazoDespacho(items, ahora,
                        parametros.plazoDespachoDiasHabiles(), parametros.plazoEncargoDiasExtra()))
                .estado(PedidoTienda.PENDIENTE)
                .entrega(entrega.getTipo())
                .fechaCreacion(ahora);
        if (envio) {
            b.envioReceptor(compra.getEnvioReceptor()).envioTelefono(compra.getEnvioTelefono())
                    .envioDireccion(compra.getEnvioDireccion()).envioCiudad(compra.getEnvioCiudad())
                    .envioIndicaciones(compra.getEnvioIndicaciones());
        } else {
            // Retiro: dónde y cuándo (copia de lo que tenía la tienda al comprar) y el código de un solo uso
            String codigo = CodigoRetiro.generar();
            codigos.put(id, codigo);
            b.tiendaDireccion(tienda != null ? tienda.getDireccion() : null)
                    .tiendaCiudad(tienda != null ? tienda.getCiudad() : null)
                    .tiendaHorario(tienda != null ? tienda.getHorario() : null)
                    .codigoRetiroHash(CodigoRetiro.hash(id, codigo))
                    .codigoRetiroUsado(false)
                    .codigoRetiroIntentos(0);
        }
        return pedidoRepository.save(b.build());
    }

    private static RepartoPago reparto(PagoEntity pago, CompraCatalogo compra, PedidoTienda pedido) {
        return RepartoPago.builder()
                .pagoId(pago.getId())
                .compraCatalogoId(compra.getId())
                .pedidoTiendaId(pedido.getId())
                .concepto(PagoMarketplaceService.CATALOGO)
                .beneficiarioTipo(RepartoPago.TIENDA)
                .beneficiarioId(pedido.getTiendaId())
                .beneficiarioNombre(pedido.getTiendaNombre())
                .monto(Dinero.aDouble(Dinero.pesos(pedido.getTotal())))
                .estado(RepartoPago.PENDIENTE)
                .liquidacionPorEvento(true)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private void deshacer(List<PedidoTienda> pedidos, List<RepartoPago> repartos) {
        try {
            repartoRepository.deleteAll(repartos);
            pedidoRepository.deleteAll(pedidos);
        } catch (Exception e) {
            log.error("No se pudieron borrar los pedidos de una compra no aplicada", e);
        }
    }

    private void noAplicada(PagoEntity pago, CompraCatalogo compra, String motivo) {
        log.warn("Pago {} de la compra {} no aplicado: {}", pago.getReferencia(), compra.getId(), motivo);
        // Solo se cierra una compra que seguía esperando el pago: una ya PAGADA no se toca
        if (CompraCatalogo.PENDIENTE_PAGO.equals(compra.getEstado())) {
            compra.setEstado(CompraCatalogo.NO_APLICADA);
            compra.setMotivoNoAplicada(motivo);
            compra.setPagoId(pago.getId());
            compraRepository.save(compra);
        }
        registrarReembolso(pago, compra.getId(), pago.getMonto(), motivo);
        avisos.compraNoAplicada(compra, pago.getMonto(), motivo);
    }

    private void registrarReembolso(PagoEntity pago, String compraId, double monto, String motivo) {
        Reembolso guardado = reembolsoRepository.save(Reembolso.builder()
                .pagoId(pago.getId())
                .compraCatalogoId(compraId != null ? compraId : pago.getCompraCatalogoId())
                .usuarioId(pago.getUsuarioId())
                .referenciaPago(pago.getReferencia())
                .transactionId(pago.getTransactionId())
                .monto(monto)
                .motivo(motivo.length() > 500 ? motivo.substring(0, 500) : motivo)
                .estado(Reembolso.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build());
        // La comisión del dinero devuelto deja de contar
        ComisionPlataforma.anular(repartoRepository, pago, monto, guardado != null ? guardado.getId() : null, null);
    }

    // ─── Consultas del cliente ───────────────────────────────────────────────

    public List<CompraDTO> misCompras(String correoCliente) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        return compraRepository.findByClienteIdOrderByFechaCreacionDesc(cliente.getId()).stream().map(c -> {
            List<CompraDTO.PedidoCompraDTO> pedidos = pedidoRepository.findByCompraIdOrderByFechaCreacionAsc(c.getId())
                    .stream().map(CompraDTO.PedidoCompraDTO::de).toList();
            return new CompraDTO(c.getId(), c.getEstado(), c.getMotivoNoAplicada(), nz(c.getSubtotal()), nz(c.getIva()),
                    nz(c.getCostoEnvio()), nz(c.getTotal()), c.getFechaCreacion(), c.getFechaPago(), c.getLineas(), pedidos);
        }).toList();
    }

    /**
     * El cliente pide un código de retiro nuevo (el del correo se perdió). El anterior deja de
     * servir: solo se guarda el hash del vigente. El código en claro NUNCA vuelve en la respuesta ni
     * se muestra en pantalla: se envía por correo al titular de la compra.
     */
    public Map<String, Object> nuevoCodigoRetiro(String correoCliente, String pedidoId) {
        Usuario cliente = usuarioService.obtenerPorCorreo(correoCliente);
        PedidoTienda pedido = pedidoRepository.findById(pedidoId)
                .filter(p -> p.esCatalogo() && cliente.getId().equals(p.getClienteId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!PedidoTienda.RETIRO.equals(pedido.getEntrega())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido es con envío: no tiene código de retiro");
        }
        String codigo = CodigoRetiro.generar();
        PedidoTienda actualizado = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(pedidoId).and("clienteId").is(cliente.getId())
                        .and("estado").in(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION, PedidoTienda.LISTO_PARA_RETIRAR)
                        .and("codigoRetiroUsado").ne(true)),
                new Update().set("codigoRetiroHash", CodigoRetiro.hash(pedidoId, codigo))
                        .set("codigoRetiroIntentos", 0).unset("codigoRetiroBloqueadoHasta"),
                FindAndModifyOptions.options().returnNew(true), PedidoTienda.class);
        if (actualizado == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya no admite un código de retiro");
        }
        avisos.codigoRetiroNuevo(actualizado, codigo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pedidoId", pedidoId);
        r.put("aviso", "Te enviamos un código de retiro nuevo a tu correo. Reemplaza al anterior y solo sirve una vez.");
        return r;
    }

    // ─── Cálculo (valida y calcula todo en el backend) ───────────────────────

    /** Resultado de validar y calcular un carrito. */
    record Calculo(List<CompraCatalogo.Linea> lineas, List<CompraCatalogo.EntregaTienda> entregas,
                   BigDecimal subtotal, BigDecimal iva, BigDecimal envio, BigDecimal total, List<String> avisos) {
    }

    Calculo calcular(Usuario cliente, CompraRequest r) {
        if (r == null || r.items() == null || r.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El carrito está vacío");
        }
        Map<String, Tienda> visibles = catalogo.tiendasVisibles();

        // Cantidades por producto (el mismo producto repetido se suma)
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        for (CompraRequest.Item item : r.items()) {
            if (item == null || item.productoId() == null || item.productoId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay un producto sin identificar en el carrito");
            }
            int cantidad = item.cantidad() != null ? item.cantidad() : 0;
            if (cantidad < 1 || cantidad > MAX_CANTIDAD) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de cada producto va de 1 a " + MAX_CANTIDAD);
            }
            cantidades.merge(item.productoId(), cantidad, Integer::sum);
        }
        if (cantidades.size() > MAX_LINEAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El carrito admite hasta " + MAX_LINEAS + " productos distintos");
        }

        Vehiculo vehiculo = r.vehiculoId() != null && !r.vehiculoId().isBlank()
                ? vehiculoDelCliente(cliente, r.vehiculoId()) : null;

        // Líneas con el precio vigente, validando producto, tienda y stock de TODAS las tiendas
        List<CompraCatalogo.Linea> lineas = new ArrayList<>();
        List<String> avisosCompatibilidad = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cantidades.entrySet()) {
            Producto p = buscarProducto(e.getKey());
            if (p == null || Boolean.FALSE.equals(p.getActivo()) || !visibles.containsKey(p.getTiendaId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un producto del carrito ya no está disponible en el catálogo. Quítalo y vuelve a intentar.");
            }
            Tienda tienda = visibles.get(p.getTiendaId());
            if (Boolean.TRUE.equals(tienda.getPlataforma())) {
                // Etapa 1 de la unificación: se muestran juntos, pero cada flujo cobra por su lado
                throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + p.getNombre()
                        + "\" lo vende DriveMaster y se paga por separado: quítalo de este carrito y pídelo por la compra normal.");
            }
            if (p.getPrecioVenta() == null || p.getPrecioVenta() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + p.getNombre() + "\" no tiene precio y no se puede comprar");
            }
            boolean encargo = "ENCARGO".equals(p.getTipo());
            int stock = p.getStockActual() != null ? p.getStockActual() : 0;
            if (!encargo && stock < e.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, stock <= 0
                        ? "\"" + p.getNombre() + "\" (" + tienda.getNombre() + ") está agotado. No se hizo ningún cobro."
                        : "De \"" + p.getNombre() + "\" (" + tienda.getNombre() + ") solo hay " + stock
                          + " disponible(s). No se hizo ningún cobro.");
            }
            lineas.add(CompraCatalogo.Linea.builder()
                    .productoId(p.getId()).productoNombre(p.getNombre()).marca(p.getMarca()).referencia(p.getReferencia())
                    .imagenUrl(p.getImagenUrl()).tiendaId(tienda.getId()).tiendaNombre(tienda.getNombre())
                    .cantidad(e.getValue()).precioUnitario(p.getPrecioVenta())
                    .subtotal(Dinero.aDouble(Dinero.pesos(BigDecimal.valueOf(p.getPrecioVenta()).multiply(BigDecimal.valueOf(e.getValue())))))
                    .tipo(encargo ? "ENCARGO" : "STOCK").diasEntrega(encargo ? p.getDiasEntregaEncargo() : null).build());
            if (vehiculo != null) {
                String estado = CompatibilidadVehiculo.estado(p, vehiculo);
                if (CompatibilidadVehiculo.NO_COMPATIBLE.equals(estado)) {
                    avisosCompatibilidad.add("\"" + p.getNombre() + "\" no es compatible con " + CompatibilidadVehiculo.describir(vehiculo));
                } else if (CompatibilidadVehiculo.POR_CONFIRMAR.equals(estado)) {
                    avisosCompatibilidad.add("\"" + p.getNombre() + "\": la tienda no declaró compatibilidades, confirma que le sirve a "
                            + CompatibilidadVehiculo.describir(vehiculo));
                }
            }
        }

        // Entrega por tienda
        Map<String, String> tipos = tiposDeEntrega(r, lineas);
        double ivaPct = parametros.ivaPct();
        List<CompraCatalogo.EntregaTienda> entregas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO, iva = BigDecimal.ZERO, envio = BigDecimal.ZERO;
        for (Map.Entry<String, String> t : tipos.entrySet()) {
            Tienda tienda = visibles.get(t.getKey());
            BigDecimal sub = lineas.stream().filter(l -> l.getTiendaId().equals(t.getKey()))
                    .map(l -> Dinero.pesos(l.getSubtotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ivaTienda = sub.multiply(BigDecimal.valueOf(ivaPct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            BigDecimal costoEnvio = BigDecimal.ZERO;
            if (PedidoTienda.ENVIO.equals(t.getValue())) {
                if (tienda.getCostoEnvio() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            tienda.getNombre() + " no hace envíos: elige retiro en la tienda para esos productos");
                }
                // La tienda solo envía dentro de su ciudad (el costo fijo no cubre otras)
                if (!CatalogoService.normalizar(tienda.getCiudad()).equals(CatalogoService.normalizar(r.envio() != null ? r.envio().ciudad() : null))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, tienda.getNombre() + " solo envía dentro de "
                            + tienda.getCiudad() + ": elige retiro en la tienda o una dirección en esa ciudad");
                }
                // PENDIENTE (ver drivemaster/PENDIENTES.md): el costo de envío se cobra tal cual, sin IVA
                costoEnvio = Dinero.pesos(tienda.getCostoEnvio());
            }
            BigDecimal totalTienda = sub.add(ivaTienda).add(costoEnvio);
            entregas.add(CompraCatalogo.EntregaTienda.builder().tiendaId(tienda.getId()).tiendaNombre(tienda.getNombre())
                    .tipo(t.getValue()).subtotal(sub.doubleValue()).iva(ivaTienda.doubleValue())
                    .costoEnvio(costoEnvio.doubleValue()).total(totalTienda.doubleValue()).build());
            subtotal = subtotal.add(sub);
            iva = iva.add(ivaTienda);
            envio = envio.add(costoEnvio);
        }
        if (hayEnvio(entregas)) validarEnvio(r.envio());
        return new Calculo(lineas, entregas, subtotal, iva, envio, subtotal.add(iva).add(envio), avisosCompatibilidad);
    }

    /** Una entrega ENVIO/RETIRO por cada tienda del carrito, ni más ni menos. */
    private static Map<String, String> tiposDeEntrega(CompraRequest r, List<CompraCatalogo.Linea> lineas) {
        Map<String, String> pedidas = new LinkedHashMap<>();
        if (r.entregas() != null) {
            for (CompraRequest.Entrega e : r.entregas()) {
                if (e == null || e.tiendaId() == null || e.tipo() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige envío o retiro para cada tienda");
                }
                String tipo = e.tipo().trim().toUpperCase();
                if (!PedidoTienda.ENVIO.equals(tipo) && !PedidoTienda.RETIRO.equals(tipo)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La entrega debe ser ENVIO o RETIRO");
                }
                if (pedidas.put(e.tiendaId(), tipo) != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una tienda tiene dos formas de entrega");
                }
            }
        }
        Map<String, String> resultado = new LinkedHashMap<>();
        for (CompraCatalogo.Linea l : lineas) {
            String tipo = pedidas.get(l.getTiendaId());
            if (tipo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Elige envío o retiro para " + l.getTiendaNombre());
            }
            resultado.put(l.getTiendaId(), tipo);
        }
        if (!resultado.keySet().containsAll(pedidas.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay una forma de entrega para una tienda que no está en el carrito");
        }
        return resultado;
    }

    private static void validarEnvio(CompraRequest.Envio e) {
        if (e == null || vacio(e.receptor()) || vacio(e.telefono()) || vacio(e.direccion()) || vacio(e.ciudad())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para el envío indica quién recibe, su teléfono, la dirección y la ciudad");
        }
    }

    private Vehiculo vehiculoDelCliente(Usuario cliente, String vehiculoId) {
        vehiculoService.obtenerDeUsuario(cliente.getCorreo(), vehiculoId); // 403/404 si no es suyo
        return vehiculoService.obtenerEntidad(vehiculoId);
    }

    private Producto buscarProducto(String id) {
        try {
            return productoService.obtenerPorId(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean hayEnvio(List<CompraCatalogo.EntregaTienda> entregas) {
        return entregas.stream().anyMatch(e -> PedidoTienda.ENVIO.equals(e.getTipo()));
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String texto(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    private static double nz(Double d) {
        return d != null ? d : 0;
    }
}
