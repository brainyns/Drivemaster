package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.CuentaBancaria;
import com.drivemaster.drivemaster.model.Diagnostico;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.service.DatosDemoCatalogo.ClienteDemo;
import com.drivemaster.drivemaster.service.DatosDemoCatalogo.Plantilla;
import com.drivemaster.drivemaster.service.DatosDemoCatalogo.TallerDemo;
import com.drivemaster.drivemaster.service.DatosDemoCatalogo.TiendaDemo;
import com.drivemaster.drivemaster.service.dispersion.SimulatedDispersionService;
import com.drivemaster.drivemaster.service.dispersion.SolicitudDispersion;
import com.drivemaster.drivemaster.util.CalculadoraCotizacion;
import com.drivemaster.drivemaster.util.DescuentoTaller;
import com.drivemaster.drivemaster.util.Dinero;
import com.drivemaster.drivemaster.util.IvaTaller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.drivemaster.drivemaster.service.DatosDemoCatalogo.*;

/**
 * Arma en memoria, sin tocar ninguna base, todo lo de la demostración: cuentas, tiendas, productos con su stock final,
 * pedidos de octubre y del 1 al 6 de noviembre de 2026 con sus movimientos, y los pagos con sus líneas de reparto (la
 * comisión la fija ComisionPlataforma.aplicar, como al aprobar un pago real). Es determinista: la misma semilla da
 * siempre los mismos datos, lo que permite saber si la base ya los tiene completos. DatosDemoService lo escribe.
 *
 * Las cuentas usan el mismo IVA de los pedidos que CompraCatalogoService.calcular: subtotal × IVA redondeado por tienda,
 * descuento de taller con DescuentoTaller.precio y envío fijo de la tienda, sin IVA y sin comisión.
 */
final class DatosDemoArmado {

    static final long SEMILLA = 20261117L;
    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final LocalDate INICIO_OCTUBRE = LocalDate.of(2026, 10, 1);
    static final LocalDate FIN_NOVIEMBRE = LocalDate.of(2026, 11, 6);
    /** Desde aquí los pedidos de noviembre pueden quedar en curso. */
    static final LocalDate EN_CURSO_DESDE = LocalDate.of(2026, 11, 4);
    static final LocalDateTime INVENTARIO_INICIAL = LocalDateTime.of(2026, 9, 28, 8, 0);

    /** Pedidos de clientes por tienda y mes (los de los talleres van aparte, ver COMPRAS_TALLER). */
    static final Map<String, int[]> CUOTAS = Map.of(
            BOCAGRANDE, new int[]{21, 7},
            MANGA, new int[]{19, 1},
            CRESPO, new int[]{11, 4},
            PRADO, new int[]{10, 4},
            EL_BOSQUE, new int[]{5, 8});

    /** Lo que se necesita de la configuración real (los mismos parámetros que usa el sistema). */
    record Parametros(double ivaPct, double comisionPct, double tarifaDiagnosticoBase, int plazoDespachoDias, int plazoEncargoExtra) {
    }

    /** Un pago con sus líneas de negocio ya calculadas. La línea PLATAFORMA la registra ComisionPlataforma al escribirlo. */
    record Venta(PagoEntity pago, List<RepartoPago> lineas, BigDecimal baseComision, String compraId, List<String> pedidoIds,
                 String ordenId, boolean esCotizacion) {
    }

    /** Un pedido de un taller que entra a su inventario al escribirse, con la fecha en que llegó. */
    record EntradaTaller(String tallerId, String pedidoId, LocalDateTime fecha) {
    }

    /**
     * Negocios reales que reciben ventas demo sin cambiar nada de ellos: las tiendas con sus productos y precios de hoy
     * y el taller con su IVA. null si no existen o no están aprobados (DatosDemoService avisa).
     */
    record Reales(Tienda vaquita, List<Producto> productosVaquita, Tienda rios, List<Producto> productosRios,
                  Taller hermanos, String correoHermanos) {
        static final Reales NINGUNO = new Reales(null, List.of(), null, List.of(), null, null);
    }

    /** El stock de un producto real antes y después de la siembra (original null: el producto no tenía el dato). */
    record AjusteStock(String productoId, String tiendaId, Integer original, int fin) {
    }

    /** Lo que una venta necesita de la tienda, sea demo o real. */
    record VTienda(String id, String nombre, String ciudad, String direccion, String horario, Double costoEnvio) {
    }

    /** Lo que una compra u orden necesita del taller, sea demo o real. */
    record VTaller(String id, String usuarioId, String nombre, String correo, String telefono, String direccion, String ciudad,
                   String mecanicoId, String mecanicoNombre, double ivaPct, double tarifaHora) {
    }

    /** Todo lo que se escribe. Cada lista va a una colección (Mongo) o tabla (Postgres). */
    static final class Lote {
        final List<Usuario> usuarios = new ArrayList<>();
        final List<Vehiculo> vehiculos = new ArrayList<>();
        final List<Tienda> tiendas = new ArrayList<>();
        final List<Taller> talleres = new ArrayList<>();
        final List<Producto> productos = new ArrayList<>();
        final List<CompraCatalogo> compras = new ArrayList<>();
        final List<PedidoTienda> pedidos = new ArrayList<>();
        final List<MovimientoInventario> movimientos = new ArrayList<>();
        final List<OrdenServicio> ordenes = new ArrayList<>();
        final List<Cotizacion> cotizaciones = new ArrayList<>();
        final List<Venta> ventas = new ArrayList<>();
        final List<EntradaTaller> entradasTaller = new ArrayList<>();
        /** Código de retiro de los pedidos LISTO_PARA_RETIRAR, para validarlos en la feria. */
        final Map<String, String> codigosRetiro = new LinkedHashMap<>();
        /** Stock de los productos reales que la siembra cambia: se aplica y se restaura con esto. */
        final List<AjusteStock> ajustesStock = new ArrayList<>();
        /** El taller real cuyo inventario recibe compras demo (null si no hay): su inventario se guarda para restaurarlo. */
        String tallerReal;
        /** Lo que no se pudo hacer como se pidió (p. ej. una tienda sin los productos esperados). */
        final List<String> avisos = new ArrayList<>();
    }

    private final Parametros parametros;
    private final Function<String, String> encriptar;
    private final Supplier<String> generarCodigo;
    private final BiFunction<String, String, String> hashCodigo;
    /** El azar de lo demo. Lo de los negocios reales usa el suyo, para que la historia de las tiendas demo no cambie. */
    private Random rnd = new Random(SEMILLA);
    private final Random rndReales = new Random(SEMILLA + 7);
    private final Lote lote = new Lote();
    /** Productos demo (se insertan). */
    private final Map<String, Producto> productos = new LinkedHashMap<>();
    /** Todos los que se pueden vender: demo y reales. Los reales no se insertan ni se cambian salvo su stock. */
    private final Map<String, Producto> catalogo = new LinkedHashMap<>();
    private final Map<String, VTienda> tiendasVenta = new LinkedHashMap<>();
    private final Map<String, VTaller> talleresVenta = new LinkedHashMap<>();
    private final Map<String, Integer> stockOriginal = new LinkedHashMap<>();
    private final java.util.Set<String> tiendasReales = new java.util.HashSet<>();
    private Reales reales = Reales.NINGUNO;
    private final Map<String, Integer> stock = new HashMap<>();
    private final Map<String, Integer> vendidas = new HashMap<>();
    private int numeroVenta;

    /**
     * @param encriptar     la contraseña con el PasswordEncoder de la aplicación
     * @param generarCodigo un código de retiro (CodigoRetiro.generar)
     * @param hashCodigo    su hash ligado al pedido (CodigoRetiro.hash), lo único que se guarda
     */
    DatosDemoArmado(Parametros parametros, Function<String, String> encriptar, Supplier<String> generarCodigo,
                    BiFunction<String, String, String> hashCodigo) {
        this.parametros = parametros;
        this.encriptar = encriptar;
        this.generarCodigo = generarCodigo;
        this.hashCodigo = hashCodigo;
    }

    Lote armar(boolean crearSuperadmin) {
        return armar(crearSuperadmin, Reales.NINGUNO);
    }

    Lote armar(boolean crearSuperadmin, Reales reales) {
        this.reales = reales;
        String password = encriptar.apply(PASSWORD);
        cuentas(password, crearSuperadmin);
        catalogoDeProductos();
        List<Pedido> plan = new ArrayList<>(planDeClientes());
        plan.addAll(comprasDeTalleres());
        plan.addAll(planReales());
        plan.addAll(comprasHermanos());
        plan.sort(Comparator.comparing(Pedido::fecha));
        List<OrdenPlan> ordenesHermanos = ordenesHermanos();
        // El stock inicial alcanza para todo lo vendido y deja el final que cuenta la historia
        for (Pedido p : plan) p.items().forEach((producto, cantidad) -> vendidas.merge(producto, cantidad, Integer::sum));
        ordenesHermanos.forEach(o -> o.repuestos().forEach((producto, cantidad) -> vendidas.merge(producto, cantidad, Integer::sum)));
        stockInicial();
        stockReales();
        for (Pedido p : plan) venta(p);
        ordenesDeTaller();
        for (OrdenPlan o : ordenesHermanos) orden(o);
        productos.values().forEach(p -> p.setStockActual(stock.get(p.getId())));
        lote.productos.addAll(productos.values());
        stockOriginal.forEach((id, original) -> lote.ajustesStock.add(
                new AjusteStock(id, catalogo.get(id).getTiendaId(), catalogo.get(id).getStockActual(), stock.get(id))));
        return lote;
    }

    // ─── Resumen (lo que deberían mostrar Informes y el asistente) ───────────

    static String mes(LocalDateTime fecha) {
        return fecha.getYear() + "-" + String.format("%02d", fecha.getMonthValue());
    }

    /** Ventas (total de los pedidos, envío incluido, como Informes) y pedidos por tienda y mes. */
    static Map<String, double[]> ventasPorTiendaYMes(Lote lote) {
        Map<String, double[]> r = new LinkedHashMap<>();
        for (PedidoTienda p : lote.pedidos) {
            double[] v = r.computeIfAbsent(p.getTiendaNombre() + " " + mes(p.getFechaCreacion()), k -> new double[2]);
            v[0] += p.getTotal();
            v[1]++;
        }
        return r;
    }

    /** Comisión retenida por mes (la de las líneas de negocio, que es la de "Comisión ganada"). */
    static Map<String, Double> comisionPorMes(Lote lote) {
        Map<String, Double> r = new LinkedHashMap<>();
        for (Venta v : lote.ventas) {
            for (RepartoPago l : v.lineas()) r.merge(mes(l.getFechaCreacion()), l.getComisionRetenida(), Double::sum);
        }
        return r;
    }

    /** Unidades por producto (nombre y marca), de todas las tiendas, de más a menos. */
    static List<Map.Entry<String, Integer>> unidadesPorProducto(Lote lote) {
        Map<String, Integer> r = new HashMap<>();
        for (PedidoTienda p : lote.pedidos) {
            for (CotizacionItem i : p.getItems()) r.merge(i.getProductoNombre() + " " + i.getMarca(), i.getCantidad(), Integer::sum);
        }
        return r.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();
    }

    static String resumen(Lote lote) {
        StringBuilder s = new StringBuilder("Datos demo:\n");
        ventasPorTiendaYMes(lote).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> s.append(String.format(
                "  %-42s %3d pedidos  %,14.0f%n", e.getKey(), (long) e.getValue()[1], e.getValue()[0])));
        comisionPorMes(lote).forEach((m, c) -> s.append(String.format("  Comisión %s: %,.0f%n", m, c)));
        unidadesPorProducto(lote).stream().limit(5).forEach(e -> s.append(String.format("  Top: %-50s %d u.%n", e.getKey(), e.getValue())));
        lote.codigosRetiro.forEach((pedido, codigo) -> s.append("  Código de retiro ").append(pedido).append(": ").append(codigo).append('\n'));
        return s.toString();
    }

    // ─── Cuentas, tiendas y talleres ─────────────────────────────────────────

    private Usuario usuario(String id, String nombre, String correo, String password, String rol, String telefono,
                            String ciudad, String direccion, LocalDateTime creado) {
        return Usuario.builder().id(id).nombre(nombre).correo(correo).password(password).proveedor("LOCAL").rol(rol)
                .permisos(RolPermisos.de(rol)).activo(true).datosCompletos(true).telefono(telefono).ciudad(ciudad)
                .direccion(direccion).fechaCreacion(creado.atZone(ZONA).toInstant()).intentosFallidos(0).bloqueado(false).build();
    }

    private static CuentaBancaria cuenta(String banco, String codigo, String numero, String titular, String nit, LocalDateTime fecha) {
        return CuentaBancaria.builder().banco(banco).bancoCodigo(codigo).tipoCuenta("CORRIENTE").numeroCuenta(numero)
                .titularNombre(titular).titularDocumentoTipo("NIT").titularDocumentoNumero(nit).fechaActualizacion(fecha).build();
    }

    private void cuentas(String password, boolean crearSuperadmin) {
        LocalDateTime registro = LocalDateTime.of(2026, 9, 15, 9, 0);
        if (crearSuperadmin) {
            lote.usuarios.add(usuario(SUPERADMIN_ID, "Administrador Demo", SUPERADMIN_CORREO, password, RolPermisos.SUPERADMIN,
                    null, "Cartagena", null, registro));
        }
        for (TiendaDemo t : TIENDAS) {
            lote.usuarios.add(usuario(t.usuarioId(), t.nombre(), t.correo(), password, RolPermisos.TIENDA, t.telefono(), t.ciudad(),
                    t.direccion(), registro));
            lote.tiendas.add(Tienda.builder().id(t.id()).usuarioId(t.usuarioId()).nombre(t.nombre()).ciudad(t.ciudad())
                    .barrio(t.barrio()).direccion(t.direccion()).latitud(t.latitud()).longitud(t.longitud()).horario(t.horario())
                    .costoEnvio(t.costoEnvio()).descuentoTalleresPct(DescuentoTaller.INICIAL_PCT).nit(t.nit())
                    .telefono(t.telefono()).correoContacto(t.correo()).estadoAprobacion(EstadoAprobacion.APROBADO).activo(true)
                    .fechaRegistro(registro).fechaAprobacion(registro.plusDays(1)).resumenSemanal(false)
                    .cuentaBancaria(cuenta(t.banco(), t.bancoCodigo(), t.cuenta(), t.nombre(), t.nit(), registro.plusDays(1)))
                    .build());
        }
        for (TallerDemo t : TALLERES) {
            talleresVenta.put(t.id(), new VTaller(t.id(), t.usuarioId(), t.nombre(), t.correo(), t.telefono(), t.direccion(), t.ciudad(),
                    t.id() + "-mecanico", t.mecanico(), parametros.ivaPct(), t.tarifaHora()));
            lote.usuarios.add(usuario(t.usuarioId(), t.nombre(), t.correo(), password, RolPermisos.TALLER, t.telefono(), t.ciudad(),
                    t.direccion(), registro));
            lote.talleres.add(Taller.builder().id(t.id()).usuarioId(t.usuarioId()).nombre(t.nombre()).nit(t.nit()).ciudad(t.ciudad())
                    .barrio(t.barrio()).direccion(t.direccion()).latitud(t.latitud()).longitud(t.longitud())
                    .especialidades(new ArrayList<>(t.especialidades())).rating(4.9).calificaciones(2)
                    .precioManoObra(t.precioManoObra()).tarifaHora(t.tarifaHora()).responsableIva(true).telefono(t.telefono())
                    .horario(t.horario()).estadoAprobacion(EstadoAprobacion.APROBADO).activo(true)
                    .fechaRegistro(registro).fechaAprobacion(registro.plusDays(1))
                    .mecanicos(new ArrayList<>(List.of(Mecanico.builder().id(t.id() + "-mecanico").nombre(t.mecanico())
                            .especialidad(t.especialidades().get(0)).activo(true).build())))
                    .cuentaBancaria(cuenta(t.banco(), t.bancoCodigo(), t.cuenta(), t.nombre(), t.nit(), registro.plusDays(1)))
                    .build());
        }
        int i = 0;
        for (ClienteDemo c : CLIENTES) {
            lote.usuarios.add(usuario(c.id(), c.nombre(), c.correo(), password, RolPermisos.CLIENTE, c.telefono(), c.ciudad(),
                    c.direccion(), LocalDateTime.of(2026, 9, 18, 10, 0).plusDays(i % 10).plusHours(i % 7)));
            for (int k = 0; k < c.vehiculos().size(); k++) {
                DatosDemoCatalogo.Vehiculo v = c.vehiculos().get(k);
                lote.vehiculos.add(Vehiculo.builder().id(vehiculoId(c, k)).usuarioId(c.id()).marca(v.marca()).modelo(v.modelo())
                        .anio(v.anio()).motor(v.motor()).combustible("Gasolina").placa(v.placa()).kilometraje(v.kilometraje())
                        .recordatoriosActivos(false).build());
            }
            i++;
        }
    }

    // ─── Productos ───────────────────────────────────────────────────────────

    private static double aCien(double valor) {
        return Math.round(valor / 100.0) * 100.0;
    }

    private void catalogoDeProductos() {
        for (TiendaDemo t : TIENDAS) {
            tiendasVenta.put(t.id(), new VTienda(t.id(), t.nombre(), t.ciudad(), t.direccion(), t.horario(), t.costoEnvio()));
            for (String clave : SURTIDO.get(t.clave())) {
                Plantilla pl = plantilla(clave);
                Producto p = new Producto();
                p.setId(productoId(t.clave(), clave));
                p.setTiendaId(t.id());
                p.setCodigo(pl.referencia());
                p.setReferencia(pl.referencia());
                p.setNombre(pl.nombre());
                p.setCategoria(pl.categoria());
                p.setMarca(pl.marca());
                p.setPrecioCompra(aCien(pl.precioCompra() * t.factorPrecio()));
                p.setPrecioVenta(aCien(pl.precioVenta() * t.factorPrecio()));
                p.setStockMinimo(pl.stockMinimo());
                p.setTipo("STOCK");
                p.setActivo(true);
                p.setFechaCreacion(INVENTARIO_INICIAL);
                p.setCompatibilidadUniversal(pl.compatibles().isEmpty());
                p.setModelosCompatibles(new ArrayList<>(pl.compatibles().stream()
                        .map(DatosDemoArmado::compatibilidad).toList()));
                productos.put(p.getId(), p);
                catalogo.put(p.getId(), p);
            }
        }
    }

    /** El stock que queda al final: lo que hace interesante "qué reponer" y "qué no se mueve". */
    private int stockFinal(Producto p) {
        String id = p.getId();
        if (id.equals(productoId(EL_BOSQUE, ESTRELLA))) return 2;
        if (id.equals(productoId(EL_BOSQUE, "aceite-5w30"))) return 1;
        if (id.equals(productoId(EL_BOSQUE, "bateria-60"))) return 1;
        if (id.equals(productoId(BOCAGRANDE, "filtro-aceite"))) return 5;
        if (id.equals(productoId(MANGA, SE_DEJA_DE_VENDER))) return 26;
        if (id.equals(productoId(MANGA, "pastillas-brembo"))) return 14;
        if (id.equals(productoId(MANGA, "amortiguador-kyb"))) return 9;
        if (id.equals(productoId(MANGA, "alternador"))) return 5;
        return p.getStockMinimo() + 3 + rnd.nextInt(12);
    }

    private void stockInicial() {
        for (Producto p : productos.values()) {
            int inicial = vendidas.getOrDefault(p.getId(), 0) + stockFinal(p);
            stock.put(p.getId(), inicial);
            lote.movimientos.add(movimiento(PREFIJO + "mi-" + p.getId().substring(PREFIJO.length()) + "-inicial", p.getId(),
                    p.getTiendaId(), "ENTRADA", inicial, 0, inicial, "Inventario inicial", "INVENTARIO-INICIAL", INVENTARIO_INICIAL,
                    PREFIJO + "u-tienda-" + clave(p.getTiendaId())));
        }
    }

    private static Compatibilidad compatibilidad(DatosDemoCatalogo.Compatible c) {
        Compatibilidad x = new Compatibilidad();
        x.setMarca(c.marca());
        x.setModelo(c.modelo());
        x.setAnoDesde(c.desde());
        x.setAnoHasta(c.hasta());
        return x;
    }

    private static MovimientoInventario movimiento(String id, String productoId, String tiendaId, String tipo, int cantidad,
                                                   int antes, int despues, String motivo, String referencia, LocalDateTime fecha,
                                                   String usuarioId) {
        MovimientoInventario m = new MovimientoInventario();
        m.setId(id);
        m.setProductoId(productoId);
        m.setTiendaId(tiendaId);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(antes);
        m.setStockNuevo(despues);
        m.setMotivo(motivo);
        m.setReferencia(referencia);
        m.setFecha(fecha);
        m.setUsuarioId(usuarioId);
        return m;
    }

    private static String clave(String tiendaId) {
        return tiendaId.substring((PREFIJO + "tienda-").length());
    }

    // ─── El plan de pedidos ──────────────────────────────────────────────────

    /** Un pedido a una tienda: qué, cuándo, cómo se entrega y en qué estado termina. */
    record Pedido(LocalDateTime fecha, String comprador, String tallerId, String tiendaId, Map<String, Integer> items,
                  String entrega, String estado) {
    }

    private double peso(String tienda, String plantilla, boolean noviembre) {
        if (MANGA.equals(tienda) && MANGA_QUIETOS.contains(plantilla)) return 0;
        if (SE_DEJA_DE_VENDER.equals(plantilla)) return noviembre ? 0 : 2.4;
        if (EL_BOSQUE.equals(tienda)) {
            switch (plantilla) {
                case "aceite-5w30": return noviembre ? 5 : 0.2;
                case "bateria-60": return noviembre ? 2.5 : 0;
                case "bateria-45": return noviembre ? 2 : 0;
                case "amortiguador-monroe": return noviembre ? 2 : 0.2;
                case "alternador": return noviembre ? 0.8 : 0;
                default: break;
            }
        }
        return switch (plantilla) {
            case ESTRELLA -> 6;
            case "filtro-aceite" -> 2.2;
            case "aceite-10w30" -> 1.6;
            case "bateria-45", "bateria-60" -> 0.6;
            case "alternador" -> 0.3;
            default -> 1;
        };
    }

    private <T> T elegir(List<T> opciones, java.util.function.ToDoubleFunction<T> peso) {
        double total = opciones.stream().mapToDouble(peso).sum();
        double r = rnd.nextDouble() * total;
        for (T o : opciones) {
            r -= peso.applyAsDouble(o);
            if (r < 0) return o;
        }
        return opciones.get(opciones.size() - 1);
    }

    private LocalDate dia(boolean noviembre) {
        LocalDate desde = noviembre ? LocalDate.of(2026, 11, 1) : INICIO_OCTUBRE;
        LocalDate hasta = noviembre ? FIN_NOVIEMBRE : LocalDate.of(2026, 10, 31);
        List<LocalDate> dias = desde.datesUntil(hasta.plusDays(1)).toList();
        return elegir(dias, d -> switch (d.getDayOfWeek()) {
            case SATURDAY -> 3.2;
            case SUNDAY -> 0.4;
            case FRIDAY -> 1.4;
            default -> 1.0;
        });
    }

    /** Sábados en la mañana y entre semana de 5 a 7 p. m., las horas con más ventas. */
    private LocalTime hora(LocalDate d) {
        double r = rnd.nextDouble();
        int minuto = rnd.nextInt(60);
        // El último día solo en la mañana: si se siembra ese mismo día, ningún pedido queda con hora futura
        if (d.equals(FIN_NOVIEMBRE)) return LocalTime.of(8 + rnd.nextInt(3), minuto);
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) return LocalTime.of(r < 0.7 ? 9 + rnd.nextInt(3) : 12 + rnd.nextInt(4), minuto);
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) return LocalTime.of(10 + rnd.nextInt(2), minuto);
        if (r < 0.55) return LocalTime.of(17 + rnd.nextInt(2), minuto);
        if (r < 0.8) return LocalTime.of(11 + rnd.nextInt(2), minuto);
        return LocalTime.of(9 + rnd.nextInt(2), minuto);
    }

    private int cantidad(Plantilla p) {
        if (ESTRELLA.equals(p.clave())) return 1 + rnd.nextInt(2);
        return switch (p.categoria()) {
            case FILTROS -> 1 + rnd.nextInt(3);
            case LUBRICANTES -> 1 + rnd.nextInt(4);
            case BATERIAS -> 1;
            default -> rnd.nextDouble() < 0.8 ? 1 : 2;
        };
    }

    private List<Pedido> planDeClientes() {
        List<Pedido> plan = new ArrayList<>();
        int enCurso = 0;
        for (TiendaDemo t : TIENDAS) {
            List<ClienteDemo> deLaCiudad = CLIENTES.stream().filter(c -> c.ciudad().equals(t.ciudad())).toList();
            for (int mes = 0; mes < 2; mes++) {
                boolean noviembre = mes == 1;
                for (int n = 0; n < CUOTAS.get(t.clave())[mes]; n++) {
                    LocalDate d = dia(noviembre);
                    LocalDateTime fecha = d.atTime(hora(d));
                    // El cliente frecuente compra en Bocagrande, El Bosque y Crespo; los demás, repartidos
                    ClienteDemo cliente = elegir(deLaCiudad, c -> FRECUENTE.equals(c.clave()) && (BOCAGRANDE.equals(t.clave())
                            || EL_BOSQUE.equals(t.clave()) || CRESPO.equals(t.clave())) ? 4.5 : 1);
                    Map<String, Integer> items = new LinkedHashMap<>();
                    int distintos = rnd.nextDouble() < 0.6 ? 1 : rnd.nextDouble() < 0.75 ? 2 : 3;
                    List<String> surtido = SURTIDO.get(t.clave());
                    for (int k = 0; k < distintos; k++) {
                        String pl = elegir(surtido.stream().filter(s -> !items.containsKey(productoId(t.clave(), s))).toList(),
                                s -> peso(t.clave(), s, noviembre));
                        if (peso(t.clave(), pl, noviembre) <= 0) continue;
                        items.put(productoId(t.clave(), pl), cantidad(plantilla(pl)));
                    }
                    if (items.isEmpty()) items.put(productoId(t.clave(), ESTRELLA), 1);
                    String entrega = t.costoEnvio() != null && rnd.nextDouble() < 0.45 ? PedidoTienda.ENVIO : PedidoTienda.RETIRO;
                    String estado = estadoFinal(entrega, d, enCurso);
                    if (!cerrado(estado)) enCurso++;
                    plan.add(new Pedido(fecha, cliente.clave(), null, t.id(), items, entrega, estado));
                }
            }
        }
        return plan;
    }

    /** Octubre y los primeros días de noviembre cerrados; del 4 al 6, en curso en sus distintos pasos. */
    private static String estadoFinal(String entrega, LocalDate d, int enCurso) {
        boolean envio = PedidoTienda.ENVIO.equals(entrega);
        if (d.isBefore(EN_CURSO_DESDE)) return envio ? PedidoTienda.ENTREGADO : PedidoTienda.RETIRADO;
        return switch (enCurso % 3) {
            case 0 -> PedidoTienda.PENDIENTE;
            case 1 -> PedidoTienda.EN_PREPARACION;
            default -> envio ? PedidoTienda.DESPACHADO : PedidoTienda.LISTO_PARA_RETIRAR;
        };
    }

    static boolean cerrado(String estado) {
        return PedidoTienda.ENTREGADO.equals(estado) || PedidoTienda.RETIRADO.equals(estado);
    }

    private static Map<String, Integer> items(String tienda, Object... claveYCantidad) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < claveYCantidad.length; i += 2) m.put(productoId(tienda, (String) claveYCantidad[i]), (Integer) claveYCantidad[i + 1]);
        return m;
    }

    /** Lo que compran los talleres para su inventario, con el descuento de la tienda. */
    private static List<Pedido> comprasDeTalleres() {
        String matuna = taller(LA_MATUNA).id();
        String popa = taller(PIE_DE_LA_POPA).id();
        return List.of(
                new Pedido(LocalDateTime.of(2026, 10, 8, 10, 15), null, matuna, tienda(EL_BOSQUE).id(),
                        items(EL_BOSQUE, ESTRELLA, 4, "filtro-aceite", 6), PedidoTienda.RETIRO, PedidoTienda.RETIRADO),
                new Pedido(LocalDateTime.of(2026, 10, 14, 16, 20), null, popa, tienda(MANGA).id(),
                        items(MANGA, SE_DEJA_DE_VENDER, 8), PedidoTienda.ENVIO, PedidoTienda.ENTREGADO),
                new Pedido(LocalDateTime.of(2026, 10, 20, 9, 40), null, matuna, tienda(BOCAGRANDE).id(),
                        items(BOCAGRANDE, "aceite-10w30", 6), PedidoTienda.RETIRO, PedidoTienda.RETIRADO),
                new Pedido(LocalDateTime.of(2026, 10, 27, 11, 5), null, popa, tienda(CRESPO).id(),
                        items(CRESPO, "filtro-aire", 4, "bujias-cobre", 2), PedidoTienda.RETIRO, PedidoTienda.RETIRADO),
                new Pedido(LocalDateTime.of(2026, 11, 3, 8, 50), null, matuna, tienda(EL_BOSQUE).id(),
                        items(EL_BOSQUE, "aceite-5w30", 6, "bateria-45", 1), PedidoTienda.RETIRO, PedidoTienda.RETIRADO),
                new Pedido(LocalDateTime.of(2026, 11, 5, 15, 30), null, popa, tienda(EL_BOSQUE).id(),
                        items(EL_BOSQUE, "amortiguador-monroe", 2), PedidoTienda.ENVIO, PedidoTienda.EN_PREPARACION));
    }

    // ─── Una venta de catálogo completa ──────────────────────────────────────

    private String numero() {
        return String.format("%04d", ++numeroVenta);
    }

    private void venta(Pedido plan) {
        // Lo de los negocios reales con su propio azar: la historia de las tiendas demo no cambia si ellos están o no
        boolean real = tiendasReales.contains(plan.tiendaId()) || (plan.tallerId() != null && !plan.tallerId().startsWith(PREFIJO));
        Random demo = rnd;
        if (real) rnd = rndReales;
        try {
            ventaDe(plan);
        } finally {
            rnd = demo;
        }
    }

    private void ventaDe(Pedido plan) {
        String n = numero();
        VTienda t = tiendasVenta.get(plan.tiendaId());
        boolean esTaller = plan.tallerId() != null;
        VTaller td = esTaller ? talleresVenta.get(plan.tallerId()) : null;
        ClienteDemo cliente = esTaller ? null : cliente(plan.comprador());
        String compradorId = esTaller ? td.usuarioId() : cliente.id();
        String compradorNombre = esTaller ? td.nombre() : cliente.nombre();
        String compradorCorreo = esTaller ? td.correo() : cliente.correo();
        LocalDateTime fecha = plan.fecha();

        // Líneas como las calcula CompraCatalogoService.calcular
        List<CompraCatalogo.Linea> lineas = new ArrayList<>();
        List<CotizacionItem> items = new ArrayList<>();
        BigDecimal sub = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> e : plan.items().entrySet()) {
            Producto p = catalogo.get(e.getKey());
            double precio = esTaller ? DescuentoTaller.precio(p.getPrecioVenta(), DescuentoTaller.INICIAL_PCT) : p.getPrecioVenta();
            double subtotal = Dinero.aDouble(Dinero.pesos(BigDecimal.valueOf(precio).multiply(BigDecimal.valueOf(e.getValue()))));
            sub = sub.add(BigDecimal.valueOf(subtotal));
            lineas.add(CompraCatalogo.Linea.builder().productoId(p.getId()).productoNombre(p.getNombre()).marca(p.getMarca())
                    .referencia(p.getReferencia()).tiendaId(t.id()).tiendaNombre(t.nombre()).cantidad(e.getValue())
                    .precioUnitario(precio).precioPublico(esTaller ? p.getPrecioVenta() : null)
                    .descuentoPct(esTaller ? DescuentoTaller.INICIAL_PCT : null).subtotal(subtotal).tipo("STOCK").build());
            items.add(CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).productoId(p.getId()).productoNombre(p.getNombre())
                    .marca(p.getMarca()).referencia(p.getReferencia()).tiendaId(t.id()).tiendaNombre(t.nombre())
                    .disponibilidad("DISPONIBLE").cantidad(e.getValue()).precioUnitario(precio).subtotal(subtotal)
                    .precioPublico(esTaller ? p.getPrecioVenta() : null).descuentoPct(esTaller ? DescuentoTaller.INICIAL_PCT : null).build());
        }
        BigDecimal iva = sub.multiply(BigDecimal.valueOf(parametros.ivaPct())).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        boolean envio = PedidoTienda.ENVIO.equals(plan.entrega());
        BigDecimal costoEnvio = envio ? Dinero.pesos(t.costoEnvio()) : BigDecimal.ZERO;
        BigDecimal total = sub.add(iva).add(costoEnvio);

        String compraId = PREFIJO + "cc-" + n;
        String pedidoId = PREFIJO + "pt-" + n;
        String referencia = REF_PAGO + "C-" + n;
        PagoEntity pago = new PagoEntity();
        pago.setConcepto(PagoMarketplaceService.CATALOGO);
        pago.setCompraCatalogoId(compraId);
        pago.setUsuarioId(compradorId);
        pago.setReferencia(referencia);
        pago.setMonto(Dinero.aDouble(total));
        pago.setMetodo("WOMPI");
        pago.setEstado("PAGADO");
        pago.setTransactionId(REF_PAGO + "TX-" + n);
        pago.setFechaCreacion(fecha.minusMinutes(3));
        pago.setFechaPago(fecha);

        String receptor = esTaller ? td.nombre() : cliente.nombre();
        String telefono = esTaller ? td.telefono() : cliente.telefono();
        String direccion = esTaller ? td.direccion() : cliente.direccion() + ", " + cliente.barrio();
        CompraCatalogo.EntregaTienda entregaTienda = CompraCatalogo.EntregaTienda.builder().tiendaId(t.id()).tiendaNombre(t.nombre())
                .tipo(plan.entrega()).subtotal(sub.doubleValue()).iva(iva.doubleValue()).costoEnvio(costoEnvio.doubleValue())
                .total(total.doubleValue()).build();
        lote.compras.add(CompraCatalogo.builder().id(compraId).clienteId(compradorId).clienteNombre(compradorNombre)
                .clienteCorreo(compradorCorreo).compradorTallerId(plan.tallerId()).lineas(lineas).entregas(new ArrayList<>(List.of(entregaTienda)))
                .envioReceptor(envio ? receptor : null).envioTelefono(envio ? telefono : null).envioDireccion(envio ? direccion : null)
                .envioCiudad(envio ? t.ciudad() : null).subtotal(sub.doubleValue()).iva(iva.doubleValue())
                .costoEnvio(costoEnvio.doubleValue()).total(total.doubleValue()).referenciaPago(referencia)
                .estado(CompraCatalogo.PAGADA).pedidoIds(new ArrayList<>(List.of(pedidoId)))
                .fechaCreacion(fecha.minusMinutes(4)).fechaPago(fecha).build());

        PedidoTienda pedido = pedido(plan, t, pedidoId, compraId, compradorId, compradorNombre, items, sub, iva, costoEnvio, total,
                receptor, telefono, direccion);
        lote.pedidos.add(pedido);

        // Stock: una SALIDA por producto, como movimientoService.registrarMovimiento
        int k = 0;
        for (CompraCatalogo.Linea l : lineas) {
            int antes = stock.get(l.getProductoId());
            int despues = antes - l.getCantidad();
            if (despues < 0) throw new IllegalStateException("El stock de " + l.getProductoId() + " quedaría negativo");
            stock.put(l.getProductoId(), despues);
            lote.movimientos.add(movimiento(PREFIJO + "mi-" + n + "-" + (++k), l.getProductoId(), t.id(), "SALIDA", l.getCantidad(),
                    antes, despues, "Compra directa del catálogo", PagoMarketplaceService.referenciaInventario(pago), fecha, compradorId));
        }

        // Reparto de la tienda: la comisión la fija ComisionPlataforma, sobre los productos con IVA y sin el envío
        RepartoPago linea = RepartoPago.builder().compraCatalogoId(compraId).pedidoTiendaId(pedidoId)
                .concepto(PagoMarketplaceService.CATALOGO).beneficiarioTipo(RepartoPago.TIENDA).beneficiarioId(t.id())
                .beneficiarioNombre(t.nombre()).monto(Dinero.aDouble(Dinero.pesos(total))).estado(RepartoPago.PENDIENTE)
                .liquidacionPorEvento(true).fechaCreacion(fecha).build();
        ComisionPlataforma.aplicar(linea, ComisionPlataforma.basePedido(pedido), parametros.comisionPct(), true);
        linea.setIvaIncluido(Dinero.aDouble(iva));
        if (cerrado(plan.estado())) {
            LocalDateTime listo = PedidoTienda.ENVIO.equals(plan.entrega()) ? pedido.getFechaRecibidoCliente() : pedido.getFechaRetirado();
            liquidar(linea, listo, PedidoTienda.ENVIO.equals(plan.entrega()) ? RecepcionEnvioService.EVENTO_CLIENTE
                    : "RETIRO_VALIDADO_POR_TIENDA", pedidoId);
        }
        lote.ventas.add(new Venta(pago, List.of(linea), ComisionPlataforma.baseConIva(sub.doubleValue(), iva.doubleValue()),
                compraId, List.of(pedidoId), null, false));
        if (esTaller && cerrado(plan.estado())) {
            LocalDateTime llego = PedidoTienda.ENVIO.equals(plan.entrega()) ? pedido.getFechaRecibidoCliente() : pedido.getFechaRetirado();
            lote.entradasTaller.add(new EntradaTaller(plan.tallerId(), pedidoId, llego.plusMinutes(40)));
        }
    }

    private PedidoTienda pedido(Pedido plan, VTienda t, String id, String compraId, String compradorId, String compradorNombre,
                                List<CotizacionItem> items, BigDecimal sub, BigDecimal iva, BigDecimal envio, BigDecimal total,
                                String receptor, String telefono, String direccion) {
        LocalDateTime fecha = plan.fecha();
        boolean esEnvio = PedidoTienda.ENVIO.equals(plan.entrega());
        String estado = plan.estado();
        PedidoTienda.PedidoTiendaBuilder b = PedidoTienda.builder().id(id).origen(PedidoTienda.ORIGEN_CATALOGO).compraId(compraId)
                .compradorTallerId(plan.tallerId()).clienteId(compradorId).clienteNombre(compradorNombre)
                .tiendaId(t.id()).tiendaNombre(t.nombre()).items(items).subtotal(sub.doubleValue()).iva(iva.doubleValue())
                .costoEnvio(envio.doubleValue()).total(total.doubleValue())
                .plazoDespachoHasta(PagoMarketplaceService.plazoDespacho(items, fecha, parametros.plazoDespachoDias(), parametros.plazoEncargoExtra()))
                .estado(estado).entrega(plan.entrega()).fechaCreacion(fecha)
                // Ya "avisado": que la alerta de despacho atrasado no le escriba al ADMIN por un pedido demo
                .alertaAtrasoEnviada(true);
        // El orden de los pasos: preparación, salida y llegada (o retiro). Lo en curso se queda en el paso de su estado.
        LocalDateTime preparacion = fecha.plusHours(2);
        boolean preparado = !PedidoTienda.PENDIENTE.equals(estado);
        if (preparado) b.fechaPreparacion(preparacion);
        if (esEnvio) {
            b.envioReceptor(receptor).envioTelefono(telefono).envioDireccion(direccion).envioCiudad(t.ciudad())
                    .envioIndicaciones("Llamar al llegar");
            if (PedidoTienda.DESPACHADO.equals(estado) || PedidoTienda.ENTREGADO.equals(estado)) {
                b.fechaDespacho(preparacion.plusHours(3)).notaDespacho("Domicilio propio de la tienda");
            }
            if (PedidoTienda.ENTREGADO.equals(estado)) {
                b.fechaRecibidoCliente(preparacion.plusHours(3).plusDays(1).withHour(11).withMinute(rnd.nextInt(50)))
                        .recepcionAutomatica(false);
            }
        } else {
            String codigo = generarCodigo.get();
            b.tiendaDireccion(t.direccion()).tiendaCiudad(t.ciudad()).tiendaHorario(t.horario())
                    .codigoRetiroHash(hashCodigo.apply(id, codigo)).codigoRetiroUsado(PedidoTienda.RETIRADO.equals(estado))
                    .codigoRetiroIntentos(0);
            if (PedidoTienda.LISTO_PARA_RETIRAR.equals(estado) || PedidoTienda.RETIRADO.equals(estado)) {
                b.fechaListoRetiro(preparacion.plusHours(1));
            }
            if (PedidoTienda.RETIRADO.equals(estado)) {
                b.fechaRetirado(preparacion.plusDays(1).withHour(10 + rnd.nextInt(7)).withMinute(rnd.nextInt(60)));
            }
            if (PedidoTienda.LISTO_PARA_RETIRAR.equals(estado)) lote.codigosRetiro.put(id, codigo);
        }
        return b.build();
    }

    /** La línea quedó lista con el evento y se liquidó en modo SIMULADO, como lo haría SimulatedDispersionService. */
    private static void liquidar(RepartoPago linea, LocalDateTime listo, String evento, String clave) {
        LocalDateTime pagado = listo.plusMinutes(30);
        linea.setEstado(RepartoPago.LIQUIDADO);
        linea.setFechaListo(listo);
        linea.setEventoListo(evento);
        linea.setFechaProcesando(pagado);
        linea.setFechaLiquidacion(pagado);
        linea.setModoLiquidacion(SimulatedDispersionService.MODO);
        linea.setReferenciaLiquidacion(REF_LIQUIDACION + clave.substring(PREFIJO.length()).toUpperCase());
        linea.setLiquidadoPor(SolicitudDispersion.SISTEMA);
        linea.setIntentosLiquidacion(1);
    }

    // ─── Órdenes de diagnóstico + cotización de los talleres ─────────────────

    /** repuestos: productoId (de una tienda demo) → cantidad; vacío si la cotización es solo de mano de obra. */
    private record OrdenPlan(String clave, String tallerId, String cliente, LocalDateTime fecha, String sintomas, String hallazgos,
                             List<String> fallas, List<CotizacionItem> manoDeObra, int horasEstimadas, Map<String, Integer> repuestos) {
    }

    private static CotizacionItem fija(String descripcion, double precio) {
        return CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).modalidad(CotizacionItem.POR_ITEM).descripcion(descripcion)
                .cantidad(1).precioUnitario(precio).build();
    }

    private static CotizacionItem porHoras(String descripcion, double horas, double tarifa) {
        return CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).modalidad(CotizacionItem.POR_HORAS).descripcion(descripcion)
                .horas(horas).precioUnitario(tarifa).build();
    }

    private void ordenesDeTaller() {
        String matuna = taller(LA_MATUNA).id();
        String popa = taller(PIE_DE_LA_POPA).id();
        List<OrdenPlan> planes = List.of(
                new OrdenPlan("os-01", matuna, "laura.perez", LocalDateTime.of(2026, 10, 9, 8, 20),
                        "Ruido metálico al frenar y el pedal se siente largo.", "Pastillas delanteras al 10 % y discos rayados.",
                        List.of("Pastillas gastadas", "Discos con surcos"),
                        List.of(fija("Cambio de pastillas y rectificación de discos", 120000), porHoras("Revisión de suspensión delantera", 1, 60000)), 3, Map.of()),
                new OrdenPlan("os-02", popa, "andres.gomez", LocalDateTime.of(2026, 10, 22, 7, 45),
                        "Se prendió el testigo de check engine y consume más gasolina.", "Sensor de oxígeno con lectura lenta y cuerpo de aceleración sucio.",
                        List.of("Sensor de oxígeno defectuoso", "Cuerpo de aceleración sucio"),
                        List.of(fija("Escaneo y cambio de sensor de oxígeno", 90000), fija("Limpieza de cuerpo de aceleración", 70000)), 2, Map.of()),
                new OrdenPlan("os-03", matuna, FRECUENTE, LocalDateTime.of(2026, 11, 2, 8, 10),
                        "Vibración en el timón a más de 60 km/h.", "Terminales de dirección con juego y llantas desbalanceadas.",
                        List.of("Terminales con juego", "Desbalanceo"),
                        List.of(fija("Alineación y balanceo", 80000), porHoras("Cambio de terminales de dirección", 1.5, 60000)), 3, Map.of()),
                new OrdenPlan("os-04", popa, "valentina.ruiz", LocalDateTime.of(2026, 11, 4, 7, 30),
                        "Le cuesta encender en las mañanas.", "Bujías desgastadas y bornes de batería sulfatados.",
                        List.of("Bujías desgastadas", "Bornes sulfatados"),
                        List.of(fija("Diagnóstico eléctrico, cambio de bujías y limpieza de bornes", 85000)), 2, Map.of()));
        for (OrdenPlan o : planes) orden(o);
    }

    // ─── Negocios reales: La Vaquita, Los Rios y Taller los hermanos ─────────

    private static boolean contiene(Producto p, String... palabras) {
        String n = CatalogoService.normalizar(p.getNombre());
        for (String palabra : palabras) if (!n.contains(palabra)) return false;
        return true;
    }

    /** Las bombas de dirección y los cuerpos de aceleración de Los Rios: lo que más vende. */
    static boolean focoDeLosRios(Producto p) {
        return contiene(p, "bomba", "direcc") || contiene(p, "cuerpo", "acelera");
    }

    static boolean vendible(Producto p) {
        return p.getPrecioVenta() != null && p.getPrecioVenta() > 0 && !"ENCARGO".equals(p.getTipo()) && !Boolean.FALSE.equals(p.getActivo());
    }

    /** Pedidos de clientes demo a las tiendas reales, con sus productos y precios de hoy. Nada de la tienda cambia salvo su stock. */
    private List<Pedido> planReales() {
        Random demo = rnd;
        rnd = rndReales;
        try {
            Taller h = reales.hermanos();
            if (h != null) {
                Mecanico m = h.getMecanicos() == null ? null : h.getMecanicos().stream()
                        .filter(x -> !Boolean.FALSE.equals(x.getActivo())).findFirst().orElse(null);
                double tarifa = h.getTarifaHora() != null && h.getTarifaHora() > 0 ? h.getTarifaHora() : 60000;
                talleresVenta.put(h.getId(), new VTaller(h.getId(), h.getUsuarioId(), h.getNombre(), reales.correoHermanos(),
                        h.getTelefono(), h.getDireccion(), h.getCiudad(), m != null ? m.getId() : null, m != null ? m.getNombre() : null,
                        IvaTaller.pct(h, parametros.ivaPct()), tarifa));
                lote.tallerReal = h.getId();
            }
            List<Pedido> plan = new ArrayList<>();
            if (reales.vaquita() != null) plan.addAll(planTiendaReal(reales.vaquita(), reales.productosVaquita(), 12, 5, true));
            if (reales.rios() != null) plan.addAll(planTiendaReal(reales.rios(), reales.productosRios(), 3, 5, false));
            return plan;
        } finally {
            rnd = demo;
        }
    }

    /**
     * estable (La Vaquita): octubre y noviembre parejos para su número de días, con un producto que sube en noviembre (el
     * de más stock). Si no (Los Rios): casi nada en octubre, y sobre todo sus bombas de dirección y cuerpos de aceleración.
     */
    private List<Pedido> planTiendaReal(Tienda t, List<Producto> productosTienda, int octubre, int noviembre, boolean estable) {
        List<Producto> vendibles = productosTienda.stream().filter(DatosDemoArmado::vendible)
                .sorted(Comparator.comparing(Producto::getId)).toList();
        if (vendibles.isEmpty()) {
            lote.avisos.add(t.getNombre() + " no tiene productos activos con precio: no se le agregan ventas.");
            return List.of();
        }
        tiendasReales.add(t.getId());
        tiendasVenta.put(t.getId(), new VTienda(t.getId(), t.getNombre(), t.getCiudad(), t.getDireccion(), t.getHorario(), t.getCostoEnvio()));
        vendibles.forEach(p -> catalogo.put(p.getId(), p));
        Producto sube = estable ? vendibles.stream()
                .max(Comparator.comparing((Producto p) -> p.getStockActual() == null ? 0 : p.getStockActual())).orElseThrow() : null;
        boolean hayFoco = vendibles.stream().anyMatch(DatosDemoArmado::focoDeLosRios);
        if (!estable && !hayFoco) {
            lote.avisos.add(t.getNombre() + " no tiene bombas de dirección ni cuerpos de aceleración: se usan sus otros productos.");
        }
        String ciudad = CatalogoService.normalizar(t.getCiudad());
        List<ClienteDemo> deLaCiudad = CLIENTES.stream().filter(c -> CatalogoService.normalizar(c.ciudad()).equals(ciudad)).toList();
        List<ClienteDemo> clientes = deLaCiudad.isEmpty()
                ? CLIENTES.stream().filter(c -> "Cartagena".equals(c.ciudad())).toList() : deLaCiudad;
        List<Pedido> plan = new ArrayList<>();
        int enCurso = 0;
        for (int mes = 0; mes < 2; mes++) {
            boolean enNoviembre = mes == 1;
            for (int n = 0; n < (enNoviembre ? noviembre : octubre); n++) {
                LocalDate d = dia(enNoviembre);
                LocalDateTime fecha = d.atTime(hora(d));
                ClienteDemo cliente = elegir(clientes, c -> 1);
                Map<String, Integer> items = new LinkedHashMap<>();
                int distintos = rnd.nextDouble() < 0.7 ? 1 : 2;
                for (int k = 0; k < distintos && items.size() < vendibles.size(); k++) {
                    Producto p = elegir(vendibles.stream().filter(x -> !items.containsKey(x.getId())).toList(), x -> estable
                            ? (x == sube ? (enNoviembre ? 9 : 0.25) : 1)
                            : (focoDeLosRios(x) ? 6 : (hayFoco ? 0.4 : 1)));
                    items.put(p.getId(), rnd.nextDouble() < 0.75 ? 1 : 2);
                }
                String entrega = t.getCostoEnvio() != null && !deLaCiudad.isEmpty() && rnd.nextDouble() < 0.4
                        ? PedidoTienda.ENVIO : PedidoTienda.RETIRO;
                String estado = estadoFinal(entrega, d, enCurso);
                if (!cerrado(estado)) enCurso++;
                plan.add(new Pedido(fecha, cliente.clave(), null, t.getId(), items, entrega, estado));
            }
        }
        return plan;
    }

    /** Lo que compra Taller los hermanos para su inventario en tiendas demo, con su 20 % de descuento. */
    private List<Pedido> comprasHermanos() {
        if (lote.tallerReal == null) return List.of();
        return List.of(
                new Pedido(LocalDateTime.of(2026, 10, 12, 10, 30), null, lote.tallerReal, tienda(BOCAGRANDE).id(),
                        items(BOCAGRANDE, ESTRELLA, 3, "liquido-dot4", 2), PedidoTienda.RETIRO, PedidoTienda.RETIRADO),
                new Pedido(LocalDateTime.of(2026, 11, 2, 9, 15), null, lote.tallerReal, tienda(EL_BOSQUE).id(),
                        items(EL_BOSQUE, "filtro-aceite", 6, "aceite-10w30", 4), PedidoTienda.RETIRO, PedidoTienda.RETIRADO));
    }

    /** Órdenes cerradas de Taller los hermanos con clientes demo: 3 en octubre y 1 en noviembre, con repuestos de Bocagrande. */
    private List<OrdenPlan> ordenesHermanos() {
        if (lote.tallerReal == null) return List.of();
        String h = lote.tallerReal;
        double tarifa = talleresVenta.get(h).tarifaHora();
        return List.of(
                new OrdenPlan("os-h1", h, "jorge.castillo", LocalDateTime.of(2026, 10, 6, 8, 0),
                        "Golpeteo en la suspensión delantera al pasar huecos.", "Amortiguadores delanteros vencidos y con fuga de aceite.",
                        List.of("Amortiguadores con fuga"), List.of(porHoras("Cambio de amortiguadores delanteros", 2, tarifa)), 3,
                        items(BOCAGRANDE, "amortiguador-monroe", 2)),
                new OrdenPlan("os-h2", h, "maria.rodriguez", LocalDateTime.of(2026, 10, 16, 7, 40),
                        "Ruido seco en la llanta delantera derecha al girar.", "Rótula de suspensión con juego.",
                        List.of("Rótula con juego"), List.of(porHoras("Cambio de rótula y alineación", 1.5, tarifa)), 2,
                        items(BOCAGRANDE, "rotula-moog", 1)),
                new OrdenPlan("os-h3", h, "sebastian.vargas", LocalDateTime.of(2026, 10, 28, 8, 30),
                        "Chillido al frenar en bajada.", "Pastillas delanteras cristalizadas.",
                        List.of("Pastillas cristalizadas"), List.of(fija("Cambio de pastillas delanteras", 70000)), 2,
                        items(BOCAGRANDE, "pastillas-brembo", 1)),
                new OrdenPlan("os-h4", h, "natalia.jimenez", LocalDateTime.of(2026, 11, 3, 7, 50),
                        "Testigo de motor encendido y tironeo en baja.", "Sensor de oxígeno sin respuesta.",
                        List.of("Sensor de oxígeno defectuoso"), List.of(fija("Escaneo y cambio de sensor de oxígeno", 90000)), 2,
                        items(BOCAGRANDE, "sensor-oxigeno", 1)));
    }

    /** El stock de los productos reales vendidos: si no alcanza, una ENTRADA demo antes de la primera venta. */
    private void stockReales() {
        for (Map.Entry<String, Integer> v : vendidas.entrySet()) {
            Producto p = catalogo.get(v.getKey());
            if (p == null || !tiendasReales.contains(p.getTiendaId())) continue;
            int original = p.getStockActual() == null ? 0 : p.getStockActual();
            int disponible = original;
            if (v.getValue() > original) {
                int entrada = v.getValue() - original + 2;
                disponible = original + entrada;
                lote.movimientos.add(movimiento(PREFIJO + "mi-real-" + p.getId() + "-entrada", p.getId(), p.getTiendaId(), "ENTRADA",
                        entrada, original, disponible, "Compra demo para la feria", REF_PAGO + "ENTRADA",
                        LocalDateTime.of(2026, 9, 30, 8, 0), null));
            }
            stock.put(p.getId(), disponible);
            stockOriginal.put(p.getId(), p.getStockActual());
        }
    }

    // ─── Una orden con su diagnóstico, su cotización y sus pagos ─────────────

    private static EventoOrden evento(String estado, LocalDateTime fecha, String rol, String actorId, String nombre, String nota) {
        return EventoOrden.builder().estado(estado).fecha(fecha).actorRol(rol).actorId(actorId).actorNombre(nombre).nota(nota).build();
    }

    private void orden(OrdenPlan o) {
        VTaller t = talleresVenta.get(o.tallerId());
        ClienteDemo c = cliente(o.cliente());
        DatosDemoCatalogo.Vehiculo v = c.vehiculos().get(0);
        String vehiculo = v.marca() + " " + v.modelo() + " " + v.anio();
        String ordenId = PREFIJO + o.clave();
        String n = o.clave().substring(3);
        String cotizacionId = PREFIJO + "cot-" + n;
        double ivaGlobal = parametros.ivaPct();
        double ivaTaller = t.ivaPct();
        double base = parametros.tarifaDiagnosticoBase();
        double tarifa = IvaTaller.conIva(base, ivaTaller);
        boolean conRepuestos = !o.repuestos().isEmpty();

        LocalDateTime creada = o.fecha();
        LocalDateTime aceptada = creada.plusMinutes(25);
        LocalDateTime pagoDiag = aceptada.plusMinutes(20);
        LocalDateTime diagnosticada = pagoDiag.plusHours(3);
        LocalDateTime cotizada = diagnosticada.plusMinutes(40);
        LocalDateTime aprobada = cotizada.plusHours(2);
        LocalDateTime pagada = aprobada.plusMinutes(5);
        LocalDateTime repuestosRecibidos = conRepuestos ? pagada.plusHours(3) : pagada.plusMinutes(1);
        LocalDateTime instalacion = conRepuestos ? repuestosRecibidos.plusMinutes(30) : aprobada.plusHours(1);
        LocalDateTime lista = instalacion.plusHours(o.horasEstimadas());
        LocalDateTime entregada = lista.plusHours(1);
        LocalDateTime recibida = entregada.plusMinutes(30);

        // Repuestos de una tienda demo, al precio de su catálogo, más la mano de obra del taller
        List<CotizacionItem> repuestos = new ArrayList<>();
        for (Map.Entry<String, Integer> r : o.repuestos().entrySet()) {
            Producto p = catalogo.get(r.getKey());
            VTienda tienda = tiendasVenta.get(p.getTiendaId());
            repuestos.add(CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).origen(CotizacionItem.ORIGEN_TIENDA)
                    .productoId(p.getId()).productoNombre(p.getNombre()).marca(p.getMarca()).referencia(p.getReferencia())
                    .tiendaId(tienda.id()).tiendaNombre(tienda.nombre()).disponibilidad("DISPONIBLE").cantidad(r.getValue())
                    .precioUnitario(p.getPrecioVenta()).build());
        }
        List<CotizacionItem> items = new ArrayList<>(repuestos);
        items.addAll(o.manoDeObra());
        CalculadoraCotizacion.Totales tot = CalculadoraCotizacion.calcular(items, ivaGlobal, ivaTaller, tarifa, parametros.comisionPct());
        Cotizacion cot = Cotizacion.builder().id(cotizacionId).ordenId(ordenId).version(1).estado(Cotizacion.APROBADA)
                .tallerId(t.id()).tallerNombre(t.nombre()).usuarioId(c.id()).vehiculoDescripcion(vehiculo).items(items)
                .subtotalRepuestos(tot.subtotalRepuestos()).subtotalRepuestosTaller(tot.subtotalRepuestosTaller())
                .subtotalManoObra(tot.subtotalManoObra()).subtotal(tot.subtotal()).ivaPct(ivaGlobal == ivaTaller ? ivaGlobal : null)
                .ivaRepuestosPct(ivaGlobal).ivaManoObraPct(ivaTaller).ivaRepuestos(tot.ivaRepuestos())
                .ivaRepuestosTaller(tot.ivaRepuestosTaller()).ivaManoObra(tot.ivaManoObra()).iva(tot.iva()).total(tot.total())
                .anticipoDiagnostico(tot.anticipo()).totalAPagar(tot.totalAPagar()).comisionPct(parametros.comisionPct())
                .comisionInformativa(tot.comision()).vigenciaHasta(cotizada.plusDays(5)).tiempoEstimadoHoras(o.horasEstimadas())
                .diasEntregaRepuestos(conRepuestos ? 1 : null).observaciones("Incluye prueba de ruta al terminar.").tokenUsado(true)
                .fechaEmision(cotizada).emitidaPor(t.usuarioId()).fechaRespuesta(aprobada).build();
        lote.cotizaciones.add(cot);

        String ref = n.toUpperCase();
        PagoEntity diag = pagoOrden(REF_PAGO + "D-" + ref, PagoMarketplaceService.DIAGNOSTICO, ordenId, null, c.id(), tarifa, pagoDiag);
        PagoEntity pagoCot = pagoOrden(REF_PAGO + "Q-" + ref, PagoMarketplaceService.COTIZACION, ordenId, cotizacionId, c.id(),
                tot.totalAPagar(), pagada);

        List<EventoOrden> eventos = new ArrayList<>(List.of(
                evento(EstadoOrden.ENVIADA, creada, EstadoOrden.CLIENTE, c.id(), c.nombre(), "Solicitud de diagnóstico"),
                evento(EstadoOrden.ASIGNADA_A_TALLER, creada.plusMinutes(1), EstadoOrden.SISTEMA, null, "DriveMaster", "Asignada a " + t.nombre()),
                evento(EstadoOrden.EN_REVISION, pagoDiag, EstadoOrden.SISTEMA, null, "DriveMaster", "Pago del diagnóstico confirmado (ref. " + diag.getReferencia() + ")"),
                evento(EstadoOrden.DIAGNOSTICADA, diagnosticada, EstadoOrden.TALLER, t.usuarioId(), t.nombre(), o.hallazgos()),
                evento(EstadoOrden.COTIZADA, cotizada, EstadoOrden.TALLER, t.usuarioId(), t.nombre(), "Cotización v1 emitida"),
                evento(EstadoOrden.APROBADA, aprobada, EstadoOrden.CLIENTE, c.id(), c.nombre(), "Cotización aprobada"),
                evento(EstadoOrden.PAGADA, pagada, EstadoOrden.SISTEMA, null, "DriveMaster", "Pago de la cotización confirmado (ref. " + pagoCot.getReferencia() + ")")));
        if (conRepuestos) {
            eventos.add(evento(EstadoOrden.REPUESTOS_PEDIDOS, pagada.plusMinutes(1), EstadoOrden.SISTEMA, null, "DriveMaster",
                    "Repuestos pedidos a 1 tienda(s)"));
            eventos.add(evento(EstadoOrden.REPUESTOS_RECIBIDOS, repuestosRecibidos, EstadoOrden.TALLER, t.usuarioId(), t.nombre(),
                    "Repuestos recibidos en el taller"));
        } else {
            eventos.add(evento(EstadoOrden.REPUESTOS_RECIBIDOS, repuestosRecibidos, EstadoOrden.SISTEMA, null, "DriveMaster",
                    "Cotización solo de mano de obra"));
        }
        eventos.add(evento(EstadoOrden.EN_INSTALACION, instalacion, EstadoOrden.TALLER, t.usuarioId(), t.nombre(), "Trabajo en curso"));
        eventos.add(evento(EstadoOrden.LISTA, lista, EstadoOrden.TALLER, t.usuarioId(), t.nombre(), "Vehículo listo"));
        eventos.add(evento(EstadoOrden.ENTREGADA, entregada, EstadoOrden.TALLER, t.usuarioId(), t.nombre(), "Vehículo entregado"));

        OrdenServicio orden = OrdenServicio.builder().id(ordenId).usuarioId(c.id()).vehiculoId(vehiculoId(c, 0))
                .vehiculoDescripcion(vehiculo).tallerId(t.id()).tallerNombre(t.nombre()).precioManoObra(tot.subtotalManoObra())
                .total(tot.total()).estado(EstadoOrden.ENTREGADA).fechaCreacion(creada).fechaPago(pagoDiag).fechaInicio(instalacion)
                .fechaFinalizacion(entregada).flujo(EstadoOrden.FLUJO_MARKETPLACE).sintomas(o.sintomas()).ciudad(c.ciudad())
                .barrio(c.barrio()).direccion(c.direccion()).preferenciaHorario("Mañana").fechaAsignacion(creada.plusMinutes(1))
                .tallerElegidoId(t.id()).tallerElegidoNombre(t.nombre()).aceptadaPorTaller(true).fechaAceptacion(aceptada)
                .mecanicoId(t.mecanicoId()).mecanicoNombre(t.mecanicoNombre()).tarifaDiagnostico(tarifa).tarifaDiagnosticoBase(base)
                .ivaDiagnosticoPct(ivaTaller).pagoDiagnosticoEstado(EstadoOrden.PAGO_PAGADO)
                .diagnostico(Diagnostico.builder().hallazgos(o.hallazgos()).fallas(new ArrayList<>(o.fallas())).urgencia("MEDIA")
                        .mecanicoId(t.mecanicoId()).mecanicoNombre(t.mecanicoNombre()).fecha(diagnosticada).build())
                .cotizacionId(cotizacionId).fechaInicioInstalacion(instalacion).fechaLista(lista).fechaEntrega(entregada)
                .recepcionConfirmada(true).fechaRecepcionCliente(recibida).recepcionAutomatica(false).liquidacionLiberada(true)
                .calificacionTaller(5).comentarioCalificacion("Muy buen servicio, me explicaron todo.").eventos(eventos).build();
        lote.ordenes.add(orden);

        // Diagnóstico: todo para el taller, con su comisión e IVA como en PagoMarketplaceService
        RepartoPago lineaDiag = lineaTaller(t, ordenId, null, PagoMarketplaceService.DIAGNOSTICO, tarifa, pagoDiag);
        BigDecimal baseDiag = ComisionPlataforma.baseDiagnostico(tarifa);
        ComisionPlataforma.aplicar(lineaDiag, baseDiag, parametros.comisionPct(), true);
        lineaDiag.setIvaIncluido(Dinero.aDouble(ComisionPlataforma.ivaDiagnostico(orden, tarifa, ivaGlobal)));
        liquidar(lineaDiag, recibida, "ORDEN_ENTREGADA", o.clave() + "-diag");
        lote.ventas.add(new Venta(diag, List.of(lineaDiag), baseDiag, null, List.of(), ordenId, false));

        // Cotización: la tienda recibe sus repuestos con IVA y el taller el resto (como PagoMarketplaceService.repartirCotizacion)
        List<RepartoPago> lineas = new ArrayList<>();
        List<String> pedidoIds = new ArrayList<>();
        BigDecimal paraTiendas = BigDecimal.ZERO;
        BigDecimal baseTiendas = BigDecimal.ZERO;
        BigDecimal ivaTiendas = BigDecimal.ZERO;
        BigDecimal baseCot = ComisionPlataforma.baseCotizacion(cot, tot.totalAPagar());
        if (conRepuestos) {
            PedidoTienda pedido = pedidoDeOrden(o, t, ordenId, cotizacionId, vehiculo, repuestos, pagada, repuestosRecibidos, pagoCot);
            lote.pedidos.add(pedido);
            pedidoIds.add(pedido.getId());
            RepartoPago lt = RepartoPago.builder().ordenServicioId(ordenId).cotizacionId(cotizacionId).pedidoTiendaId(pedido.getId())
                    .concepto(PagoMarketplaceService.COTIZACION).beneficiarioTipo(RepartoPago.TIENDA).beneficiarioId(pedido.getTiendaId())
                    .beneficiarioNombre(pedido.getTiendaNombre()).monto(Dinero.aDouble(Dinero.pesos(pedido.getTotal())))
                    .estado(RepartoPago.PENDIENTE).liquidacionPorEvento(true).fechaCreacion(pagada).build();
            ComisionPlataforma.aplicar(lt, ComisionPlataforma.basePedido(pedido), parametros.comisionPct(), true);
            lt.setIvaIncluido(Dinero.aDouble(Dinero.pesos(pedido.getIva())));
            liquidar(lt, repuestosRecibidos, "RECEPCION_CONFIRMADA_TALLER", pedido.getId());
            lineas.add(lt);
            paraTiendas = paraTiendas.add(Dinero.pesos(pedido.getTotal()));
            baseTiendas = baseTiendas.add(ComisionPlataforma.basePedido(pedido));
            ivaTiendas = ivaTiendas.add(Dinero.pesos(pedido.getIva()));
        }
        BigDecimal paraTaller = Dinero.pesos(tot.totalAPagar()).subtract(paraTiendas).max(BigDecimal.ZERO);
        RepartoPago lineaCot = lineaTaller(t, ordenId, cotizacionId, PagoMarketplaceService.COTIZACION, Dinero.aDouble(paraTaller), pagada);
        ComisionPlataforma.aplicar(lineaCot, baseCot.subtract(baseTiendas), parametros.comisionPct(), true);
        lineaCot.setIvaIncluido(Dinero.aDouble(ComisionPlataforma.ivaCotizacionTaller(cot, orden, ivaTiendas, ivaGlobal)));
        liquidar(lineaCot, recibida, "ORDEN_ENTREGADA", o.clave() + "-cot");
        lineas.add(lineaCot);
        lote.ventas.add(new Venta(pagoCot, lineas, baseCot, null, pedidoIds, ordenId, true));
    }

    /** El pedido de los repuestos a la tienda (origen TALLER), recibido en el taller, con su SALIDA de stock. */
    private PedidoTienda pedidoDeOrden(OrdenPlan o, VTaller t, String ordenId, String cotizacionId, String vehiculo,
                                       List<CotizacionItem> repuestos, LocalDateTime pagada, LocalDateTime recibido, PagoEntity pago) {
        VTienda tienda = tiendasVenta.get(repuestos.get(0).getTiendaId());
        BigDecimal sub = BigDecimal.ZERO;
        int k = 0;
        for (CotizacionItem i : repuestos) {
            sub = sub.add(Dinero.pesos(i.getSubtotal()));
            int antes = stock.get(i.getProductoId());
            int despues = antes - i.getCantidad();
            if (despues < 0) throw new IllegalStateException("El stock de " + i.getProductoId() + " quedaría negativo");
            stock.put(i.getProductoId(), despues);
            lote.movimientos.add(movimiento(PREFIJO + "mi-" + o.clave() + "-" + (++k), i.getProductoId(), tienda.id(), "SALIDA",
                    i.getCantidad(), antes, despues, "Repuestos de la cotización de " + t.nombre(),
                    PagoMarketplaceService.referenciaInventario(pago), pagada, t.usuarioId()));
        }
        BigDecimal iva = sub.multiply(BigDecimal.valueOf(parametros.ivaPct())).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return PedidoTienda.builder().id(PREFIJO + "pt-" + o.clave().substring(3)).origen(PedidoTienda.ORIGEN_TALLER).ordenId(ordenId)
                .cotizacionId(cotizacionId).tiendaId(tienda.id()).tiendaNombre(tienda.nombre()).tallerId(t.id()).tallerNombre(t.nombre())
                .tallerDireccion(t.direccion()).tallerCiudad(t.ciudad()).tallerTelefono(t.telefono()).vehiculoDescripcion(vehiculo)
                .items(repuestos).subtotal(sub.doubleValue()).iva(iva.doubleValue()).total(sub.add(iva).doubleValue())
                .plazoDespachoHasta(PagoMarketplaceService.plazoDespacho(repuestos, pagada, parametros.plazoDespachoDias(), parametros.plazoEncargoExtra()))
                .alertaAtrasoEnviada(true).estado(PedidoTienda.RECIBIDO).fechaCreacion(pagada)
                .fechaPreparacion(pagada.plusMinutes(40)).fechaDespacho(pagada.plusHours(2)).notaDespacho("Domicilio propio de la tienda")
                .fechaRecibido(recibido).build();
    }

    private static PagoEntity pagoOrden(String referencia, String concepto, String ordenId, String cotizacionId, String usuarioId,
                                        double monto, LocalDateTime fecha) {
        PagoEntity p = new PagoEntity();
        p.setConcepto(concepto);
        p.setOrdenServicioId(ordenId);
        p.setCotizacionId(cotizacionId);
        p.setUsuarioId(usuarioId);
        p.setReferencia(referencia);
        p.setMonto(monto);
        p.setMetodo("WOMPI");
        p.setEstado("PAGADO");
        p.setTransactionId(referencia.replace(REF_PAGO, REF_PAGO + "TX-"));
        p.setFechaCreacion(fecha.minusMinutes(2));
        p.setFechaPago(fecha);
        return p;
    }

    private static RepartoPago lineaTaller(VTaller t, String ordenId, String cotizacionId, String concepto, double monto,
                                           LocalDateTime fecha) {
        return RepartoPago.builder().ordenServicioId(ordenId).cotizacionId(cotizacionId).concepto(concepto)
                .beneficiarioTipo(RepartoPago.TALLER).beneficiarioId(t.id()).beneficiarioNombre(t.nombre())
                .monto(Dinero.aDouble(Dinero.pesos(monto))).estado(RepartoPago.PENDIENTE).liquidacionPorEvento(true)
                .fechaCreacion(fecha).build();
    }
}
