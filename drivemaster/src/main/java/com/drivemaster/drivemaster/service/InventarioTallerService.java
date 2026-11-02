package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.PiezaTallerRepository;
import com.drivemaster.drivemaster.util.Dinero;
import com.drivemaster.drivemaster.util.IvaTaller;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * "Mi inventario" del taller. El taller sale siempre de la sesión, nunca de un parámetro.
 *
 * Una pieza comprada queda "En camino" (se deduce de sus pedidos) y solo entra al inventario cuando el taller confirma
 * que le llegó. En un envío esa confirmación es también la recepción que habilita el pago de la tienda. Si la recepción
 * se confirmó sola por plazo, o la tienda validó el retiro, la tienda cobra igual y las piezas siguen sin entrar hasta
 * que el taller confirme.
 */
@Service
public class InventarioTallerService {

    private static final double PRECIO_MAXIMO = 100_000_000;
    // Ya no van a llegar: no se muestran en camino
    private static final Set<String> SIN_ENTREGA = Set.of(PedidoTienda.NO_DESPACHADO, PedidoTienda.NO_ENTREGADO);

    private final TallerService tallerService;
    private final PiezaTallerRepository piezas;
    private final PedidoTiendaRepository pedidos;
    private final ProductoService productoService;
    private final StockTallerService stock;
    private final RecepcionEnvioService recepcion;
    private final MongoTemplate mongoTemplate;
    private final ParametrosMarketplace parametros;
    private final com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository reembolsos;
    private final PrecioPublicoTaller precioPublico;

    public InventarioTallerService(TallerService tallerService, PiezaTallerRepository piezas, PedidoTiendaRepository pedidos,
                                   ProductoService productoService, StockTallerService stock, RecepcionEnvioService recepcion,
                                   MongoTemplate mongoTemplate, ParametrosMarketplace parametros,
                                   com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository reembolsos,
                                   PrecioPublicoTaller precioPublico) {
        this.tallerService = tallerService;
        this.piezas = piezas;
        this.pedidos = pedidos;
        this.productoService = productoService;
        this.stock = stock;
        this.recepcion = recepcion;
        this.mongoTemplate = mongoTemplate;
        this.parametros = parametros;
        this.reembolsos = reembolsos;
        this.precioPublico = precioPublico;
    }

    public InventarioTallerDTO.Inventario inventario(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        double pct = parametros.comisionPct();
        List<PiezaTaller> propias = piezas.findByTallerIdOrderByNombreAsc(taller.getId());
        List<InventarioTallerDTO.PedidoEnCamino> enCamino = pedidos
                .findByCompradorTallerIdAndFechaEntradaInventarioIsNullOrderByFechaCreacionDesc(taller.getId()).stream()
                .filter(p -> !SIN_ENTREGA.contains(p.getEstado())).map(InventarioTallerService::enCamino).toList();
        double aCosto = 0;
        double aVenta = 0;
        for (PiezaTaller p : propias) {
            for (PiezaTaller.Lote l : p.getLotes()) aCosto += l.getDisponible() * valor(l.getCostoUnitario());
            aVenta += p.getStock() * valor(p.getPrecioVenta());
        }
        return new InventarioTallerDTO.Inventario(propias.stream().map(p -> pieza(p, pct, taller)).toList(), enCamino, pct,
                parametros.comisionRetenida(), PiezaTaller.RECARGO_SUGERIDO_PCT, aCosto, aVenta,
                reembolsos.saldosPendientesDe(com.drivemaster.drivemaster.model.mysql.RepartoPago.TALLER, taller.getId()).stream()
                        .map(b -> new InventarioTallerDTO.Saldo(b.getId(), b.getSaldoAFavor(), b.getMotivo(), b.getOrdenServicioId(), b.getFechaCreacion()))
                        .toList());
    }

    /**
     * El precio es libre, mayor que 0 y antes de IVA, pero nunca por encima del precio público: el cliente no puede pagar
     * más por la pieza del taller que en la tienda. Vender en pérdida se avisa, pero es decisión del taller.
     */
    public InventarioTallerDTO.Pieza cambiarPrecio(String correoTaller, String piezaId, Double precio) {
        Taller taller = operativo(correoTaller);
        if (precio == null || precio <= 0 || precio > PRECIO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor que 0");
        }
        double pesos = Dinero.aDouble(Dinero.pesos(precio));
        if (pesos <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor que 0");
        PiezaTaller actual = piezas.findById(piezaId).filter(p -> taller.getId().equals(p.getTallerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pieza no encontrada"));
        PrecioPublicoTaller.Referencia ref = precioPublico.referencia(actual);
        if (ref != null) {
            double tope = precioPublico.tope(ref.precioPublico(), taller);
            if (pesos > tope) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu precio no puede superar el precio público: máximo "
                        + pesosTexto(tope) + " antes de IVA. Con eso tu cliente paga " + pesosTexto(precioPublico.finalEnTienda(ref.precioPublico()))
                        + ", lo mismo que en " + ref.tiendaNombre() + ".");
            }
        }
        return pieza(stock.cambiarPrecio(taller.getId(), piezaId, pesos), parametros.comisionPct(), taller);
    }

    private static String pesosTexto(double v) {
        return "$" + java.text.NumberFormat.getIntegerInstance(new java.util.Locale("es", "CO")).format(Math.round(v));
    }

    /**
     * El taller confirma que le llegó el pedido: sus líneas entran como lotes con su costo. Repetirla no suma dos veces.
     * Un pedido de otro, o de una compra de cliente, responde 404.
     */
    public InventarioTallerDTO.Inventario confirmarLlegada(String correoTaller, String pedidoId) {
        Taller taller = operativo(correoTaller);
        PedidoTienda pedido = pedidos.findById(pedidoId).filter(p -> taller.getId().equals(p.getCompradorTallerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (pedido.getFechaEntradaInventario() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya está en tu inventario");
        }
        if (Boolean.TRUE.equals(pedido.getProblemaReportado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reportaste un problema con este pedido: DriveMaster lo está revisando");
        }
        if (!llego(pedido)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PedidoTienda.RETIRO.equals(pedido.getEntrega())
                    ? "Primero retira el pedido en la tienda con tu código" : "La tienda aún no ha despachado este pedido");
        }
        if (PedidoTienda.DESPACHADO.equals(pedido.getEstado())) recepcion.confirmarRecepcion(correoTaller, pedidoId);

        LocalDateTime ahora = entrarPedido(taller, pedido, pedido.getClienteId());
        // Al final: si algo falla a mitad, repetir la confirmación completa lo que faltó sin duplicar los lotes ya creados
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(pedidoId).and("fechaEntradaInventario").is(null)),
                new Update().set("fechaEntradaInventario", ahora), PedidoTienda.class);
        return inventario(correoTaller);
    }


    /**
     * Las líneas de un pedido entran al inventario del taller como lotes, con el costo de la regla D2: lo que cobró la
     * tienda, sin IVA si el taller lo descuenta y con IVA si no es responsable. El lote se llama pedidoId|productoId: entrar
     * dos veces el mismo pedido no suma dos veces. Lo usan la llegada de una compra y la cancelación de una orden cuyos
     * repuestos de tienda ya estaban en el taller.
     */
    public LocalDateTime entrarPedido(Taller taller, PedidoTienda pedido, String usuarioId) {
        LocalDateTime ahora = LocalDateTime.now();
        double ivaPorPeso = valor(pedido.getSubtotal()) > 0 ? valor(pedido.getIva()) / pedido.getSubtotal() : 0;
        boolean ivaEnCosto = !IvaTaller.esResponsable(taller);
        for (CotizacionItem item : pedido.getItems() != null ? pedido.getItems() : List.<CotizacionItem>of()) {
            if (item.getCantidad() == null || item.getCantidad() <= 0) continue;
            // Lo congelado: el público de la compra (o el del producto hoy, en compras anteriores al descuento) y lo pagado
            Producto producto = producto(item.getProductoId());
            Double publico = item.getPrecioPublico() != null ? item.getPrecioPublico()
                    : producto != null && producto.getPrecioVenta() != null ? producto.getPrecioVenta() : item.getPrecioUnitario();
            PiezaTaller datos = datosDePieza(item);
            if (publico != null && publico > 0) datos.setPrecioVenta(precioPublico.tope(publico, taller));
            stock.entrar(new StockTallerService.Entrada(taller.getId(), datos, PiezaTaller.Lote.builder()
                    .id(pedido.getId() + "|" + item.getProductoId()).compraId(pedido.getCompraId()).pedidoId(pedido.getId())
                    .productoId(item.getProductoId()).tiendaId(pedido.getTiendaId()).tiendaNombre(pedido.getTiendaNombre())
                    .cantidad(item.getCantidad()).disponible(item.getCantidad())
                    .costoUnitario(costo(item.getPrecioUnitario(), ivaEnCosto ? ivaPorPeso : 0)).ivaEnCosto(ivaEnCosto)
                    .precioPublico(publico).descuentoPct(item.getDescuentoPct()).precioPagado(item.getPrecioUnitario())
                    .fechaEntrada(ahora).build(), usuarioId));
        }
        return ahora;
    }
    /** Ya salió de la tienda hacia el taller. Una entrega confirmada sola por plazo también cuenta. */
    private static boolean llego(PedidoTienda p) {
        return PedidoTienda.RETIRO.equals(p.getEntrega())
                ? PedidoTienda.RETIRADO.equals(p.getEstado())
                : PedidoTienda.DESPACHADO.equals(p.getEstado()) || PedidoTienda.ENTREGADO.equals(p.getEstado());
    }

    /** Sin IVA si el taller lo descuenta; con IVA si no es responsable, porque entonces es parte de lo que le costó. */
    private static double costo(Double precioUnitario, double ivaPorPeso) {
        return Math.round(valor(precioUnitario) * (1 + ivaPorPeso));
    }

    /** Copia del producto de hoy; si la tienda lo borró, lo que quedó en el pedido. */
    private PiezaTaller datosDePieza(CotizacionItem item) {
        Producto producto = producto(item.getProductoId());
        Producto base = producto != null ? producto : desdeItem(item);
        return PiezaTaller.builder().clave(BuscadorRepuestosService.clavePieza(base)).nombre(base.getNombre()).marca(base.getMarca())
                .referencia(base.getReferencia()).categoria(base.getCategoria()).imagenUrl(base.getImagenUrl())
                .modelosCompatibles(base.getModelosCompatibles()).compatibilidadUniversal(base.getCompatibilidadUniversal()).build();
    }

    private Producto producto(String id) {
        try {
            return productoService.obtenerPorId(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Producto desdeItem(CotizacionItem item) {
        Producto p = new Producto();
        p.setNombre(item.getProductoNombre());
        p.setMarca(item.getMarca());
        p.setReferencia(item.getReferencia());
        return p;
    }

    private Taller operativo(String correoTaller) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion()) || !Boolean.TRUE.equals(taller.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller no está habilitado para operar");
        }
        return taller;
    }

    /**
     * Las cifras de una pieza. El precio que cuenta es el que se cobraría hoy: el del taller, o el tope si el precio
     * público bajó y el del taller quedó por encima. enPerdida: con ese precio no cubre costo y comisión.
     */
    private InventarioTallerDTO.Pieza pieza(PiezaTaller p, double comisionPct, Taller taller) {
        Double costo = costoActual(p);
        PrecioPublicoTaller.Referencia ref = precioPublico.referencia(p);
        Double tope = ref == null ? null : precioPublico.tope(ref.precioPublico(), taller);
        Double precio = p.getPrecioVenta();
        Double cobrado = precio == null ? null : tope != null ? (Double) Math.min(precio, tope) : precio;
        Double recargo = null;
        Double margen = null;
        Double comision = null;
        Double ganancia = null;
        if (cobrado != null && cobrado > 0 && costo != null) {
            comision = Dinero.aDouble(ComisionPlataforma.monto(Dinero.pesos(cobrado), comisionPct));
            ganancia = cobrado - costo - comision;
            margen = porcentaje(cobrado - costo, cobrado);
            recargo = costo > 0 ? porcentaje(cobrado - costo, costo) : null;
        }
        Double gananciaConTope = null;
        if (tope != null && costo != null) {
            gananciaConTope = tope - costo - Dinero.aDouble(ComisionPlataforma.monto(Dinero.pesos(tope), comisionPct));
        }
        List<InventarioTallerDTO.Lote> lotes = p.getLotes().stream().sorted(FIFO)
                .map(l -> new InventarioTallerDTO.Lote(l.getId(), l.getCompraId(), l.getPedidoId(), l.getTiendaNombre(), l.getCantidad(),
                        l.getDisponible(), l.getCostoUnitario(), l.getIvaEnCosto(), l.getFechaEntrada(), l.getPrecioPublico(),
                        l.getDescuentoPct(), l.getPrecioPagado())).toList();
        return new InventarioTallerDTO.Pieza(p.getId(), p.getNombre(), p.getMarca(), p.getReferencia(), p.getCategoria(), p.getImagenUrl(),
                p.getStock(), precio, costo, recargo, margen, comision, ganancia, ganancia != null && ganancia < 0,
                p.getCompatibilidadUniversal(), p.getModelosCompatibles(), lotes,
                ref == null ? null : ref.precioPublico(), ref == null ? null : precioPublico.finalEnTienda(ref.precioPublico()),
                ref == null ? null : ref.tiendaNombre(), tope, cobrado,
                precio != null && tope != null && precio > tope, gananciaConTope != null && gananciaConTope < 0);
    }

    private static final Comparator<PiezaTaller.Lote> FIFO = Comparator.comparing(PiezaTaller.Lote::getFechaEntrada,
            Comparator.nullsFirst(Comparator.naturalOrder()));

    /** El del próximo lote en salir; sin stock, el del último que entró. */
    private static Double costoActual(PiezaTaller p) {
        List<PiezaTaller.Lote> enOrden = p.getLotes().stream().sorted(FIFO).toList();
        return enOrden.stream().filter(l -> l.getDisponible() > 0).findFirst()
                .or(() -> enOrden.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(enOrden.get(enOrden.size() - 1)))
                .map(PiezaTaller.Lote::getCostoUnitario).orElse(null);
    }

    /** Una cifra decimal: 35,0. */
    private static double porcentaje(double parte, double total) {
        return BigDecimal.valueOf(parte * 100 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static InventarioTallerDTO.PedidoEnCamino enCamino(PedidoTienda p) {
        boolean problema = Boolean.TRUE.equals(p.getProblemaReportado());
        boolean retiro = PedidoTienda.RETIRO.equals(p.getEntrega());
        List<InventarioTallerDTO.LineaEnCamino> lineas = new ArrayList<>();
        if (p.getItems() != null) {
            for (CotizacionItem i : p.getItems()) {
                lineas.add(new InventarioTallerDTO.LineaEnCamino(i.getProductoNombre(), i.getMarca(), i.getReferencia(),
                        i.getCantidad() != null ? i.getCantidad() : 0, i.getPrecioUnitario()));
            }
        }
        return new InventarioTallerDTO.PedidoEnCamino(p.getId(), p.getCompraId(), p.getTiendaNombre(), p.getEntrega(), p.getEstado(),
                llego(p) && !problema, problema, !retiro && PedidoTienda.DESPACHADO.equals(p.getEstado()) && !problema,
                retiro && !PedidoTienda.RETIRADO.equals(p.getEstado()), retiro ? p.getTiendaDireccion() : null,
                retiro ? p.getTiendaCiudad() : null, retiro ? p.getTiendaHorario() : null, p.getFechaCreacion(), lineas);
    }

    private static double valor(Double v) {
        return v != null ? v : 0;
    }
}
