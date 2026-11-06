package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.security.RolPermisos;
import com.drivemaster.drivemaster.util.CodigoRetiro;
import com.drivemaster.drivemaster.util.NitColombia;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.drivemaster.drivemaster.service.DatosDemoCatalogo.PREFIJO;

/**
 * Siembra y borra los datos de demostración de la feria (ver DatosDemoArmado y DatosDemoCatalogo). Solo lo ejecuta
 * DatosDemoRunner, con el perfil demo-seed. No envía correos ni llama a Wompi: escribe directo en Mongo y Postgres.
 *
 * Qué es demo: en Mongo, todo _id que empieza por "demo-"; en Postgres, los pagos DEMO-… y sus líneas (y las
 * liquidadas SIM-DEMO-…). Borrar quita solo eso, más lo que un usuario demo haya creado durante la feria en colecciones
 * que solo le pertenecen a él (sus vehículos, notificaciones, horario, proveedores…). Lo que pueda llevar dinero real
 * (pedidos, órdenes o líneas de pago reales que toquen un negocio demo) no se borra: se cuenta y se avisa.
 *
 * Sin dejar datos a medias: una marca (colección demo_datos) dice si la siembra terminó. Si una siembra falla, se borra
 * lo escrito; si quedó a medias por otra razón (se cortó el proceso), la siguiente siembra lo detecta, borra y empieza.
 */
@Service
public class DatosDemoService {

    private static final Logger log = LoggerFactory.getLogger(DatosDemoService.class);

    static final String COLECCION_MARCA = "demo_datos";
    static final String ID_MARCA = PREFIJO + "estado";
    /** Anclado al inicio: "demo-" en medio de un id real no coincide. */
    static final Pattern ES_DEMO = Pattern.compile("^demo-");

    /** Colecciones donde la demo escribe documentos con _id "demo-…" (borrar los quita por _id). */
    static final List<String> COLECCIONES_POR_ID = List.of("usuarios", "vehiculos", "tiendas", "talleres", "productos",
            "compras_catalogo", "pedidos_tienda", "movimientos_inventario", "ordenes_servicio", "cotizaciones", COLECCION_MARCA);

    /**
     * Colección → campo dueño: documentos que pertenecen solo a un usuario, taller o tienda demo, sin dinero de por
     * medio. La demo crea los de piezas_taller y movimientos_pieza_taller; los demás solo existen si alguien usó una
     * cuenta demo durante la feria.
     */
    static final Map<String, String> COLECCIONES_POR_DUENO = Map.ofEntries(
            Map.entry("piezas_taller", "tallerId"),
            Map.entry("movimientos_pieza_taller", "tallerId"),
            Map.entry("horarios_taller", "tallerId"),
            Map.entry("cambios_taller", "tallerId"),
            Map.entry("productos", "tiendaId"),
            Map.entry("movimientos_inventario", "tiendaId"),
            Map.entry("proveedores", "tiendaId"),
            Map.entry("compras", "tiendaId"),
            Map.entry("vehiculos", "usuarioId"),
            Map.entry("notificaciones", "usuarioId"),
            Map.entry("recuperaciones_clave", "usuarioId"),
            Map.entry("cambios_correo", "usuarioId"));

    private final MongoTemplate mongo;
    private final PagoRepository pagos;
    private final RepartoPagoRepository repartos;
    private final InventarioTallerService inventarioTaller;
    private final ParametrosMarketplace parametros;
    private final PasswordEncoder passwordEncoder;

    public DatosDemoService(MongoTemplate mongo, PagoRepository pagos, RepartoPagoRepository repartos,
                            InventarioTallerService inventarioTaller, ParametrosMarketplace parametros, PasswordEncoder passwordEncoder) {
        this.mongo = mongo;
        this.pagos = pagos;
        this.repartos = repartos;
        this.inventarioTaller = inventarioTaller;
        this.parametros = parametros;
        this.passwordEncoder = passwordEncoder;
    }

    static Query demoPorId() {
        return Query.query(Criteria.where("_id").regex(ES_DEMO));
    }

    static Query demoPor(String campo) {
        return Query.query(Criteria.where(campo).regex(ES_DEMO));
    }

    /** Lo que hay de la demo en la base y si la última siembra terminó. */
    record Estado(long documentosMongo, long pagos, long repartos, boolean completa) {
        boolean vacia() {
            return documentosMongo == 0 && pagos == 0 && repartos == 0;
        }
    }

    Estado estado() {
        long docs = 0;
        for (String c : COLECCIONES_POR_ID) docs += mongo.count(demoPorId(), c);
        Document marca = mongo.findById(ID_MARCA, Document.class, COLECCION_MARCA);
        return new Estado(docs, pagos.contarPagosDemo(), repartos.contarRepartosDemo(),
                marca != null && Boolean.TRUE.equals(marca.getBoolean("completo")));
    }

    /** Para el comando "estado": cuánto hay de la demo y si terminó. */
    public String descripcionEstado() {
        Estado e = estado();
        if (e.vacia()) return "no hay datos demo";
        return e.documentosMongo() + " documentos en Mongo, " + e.pagos() + " pagos y " + e.repartos() + " líneas en Postgres; "
                + (e.completa() ? "la siembra terminó" : "INCOMPLETO: corre borrar o vuelve a sembrar");
    }

    DatosDemoArmado.Lote armar(boolean crearSuperadmin) {
        DatosDemoArmado.Parametros p = new DatosDemoArmado.Parametros(parametros.ivaPct(), parametros.comisionPct(),
                parametros.tarifaDiagnostico(), parametros.plazoDespachoDiasHabiles(), parametros.plazoEncargoDiasExtra());
        return new DatosDemoArmado(p, passwordEncoder::encode, CodigoRetiro::generar, CodigoRetiro::hash).armar(crearSuperadmin);
    }

    // ─── Sembrar ─────────────────────────────────────────────────────────────

    /** Siembra todo una sola vez. Devuelve el resumen de lo sembrado (o dice que ya estaba). */
    public String sembrar() {
        Estado antes = estado();
        if (antes.completa()) {
            return "Los datos demo ya estaban completos: no se escribió nada. Para regenerarlos, primero borra.";
        }
        boolean hayAdmin = mongo.exists(Query.query(Criteria.where("rol").is(RolPermisos.SUPERADMIN)
                .and("_id").not().regex(ES_DEMO)), Usuario.class);
        DatosDemoArmado.Lote lote = armar(!hayAdmin);
        List<String> choques = choques(lote);
        if (!choques.isEmpty()) {
            throw new IllegalStateException("No se sembró nada: estos datos demo chocan con datos reales:\n - "
                    + String.join("\n - ", choques));
        }
        if (!antes.vacia()) {
            log.warn("Había datos demo de una siembra que no terminó ({} documentos, {} pagos): se borran antes de sembrar",
                    antes.documentosMongo(), antes.pagos());
            borrar();
        }
        mongo.save(new Document("_id", ID_MARCA).append("completo", false).append("inicio", LocalDateTime.now()), COLECCION_MARCA);
        try {
            escribir(lote);
        } catch (RuntimeException e) {
            log.error("La siembra de datos demo falló: se borra lo que alcanzó a escribir", e);
            try {
                borrar();
            } catch (RuntimeException limpieza) {
                log.error("Tampoco se pudo borrar: corre DEMO_ACCION=borrar", limpieza);
                e.addSuppressed(limpieza);
            }
            throw e;
        }
        mongo.save(new Document("_id", ID_MARCA).append("completo", true).append("fin", LocalDateTime.now())
                .append("pagos", lote.ventas.size()).append("pedidos", lote.pedidos.size()), COLECCION_MARCA);
        return DatosDemoArmado.resumen(lote);
    }

    /** Correos, NIT o nombres demo que ya usa algo que no es demo. Si hay alguno, no se siembra nada. */
    List<String> choques(DatosDemoArmado.Lote lote) {
        List<String> r = new ArrayList<>();
        for (Usuario u : lote.usuarios) {
            Query mismoCorreo = Query.query(Criteria.where("correo").regex(regexExacto(u.getCorreo()), "i")
                    .and("_id").not().regex(ES_DEMO));
            if (mongo.exists(mismoCorreo, Usuario.class)) r.add("ya existe un usuario con el correo " + u.getCorreo());
        }
        List<Tienda> tiendasReales = mongo.find(Query.query(Criteria.where("_id").not().regex(ES_DEMO)), Tienda.class);
        List<Taller> talleresReales = mongo.find(Query.query(Criteria.where("_id").not().regex(ES_DEMO)), Taller.class);
        for (Tienda t : lote.tiendas) {
            for (Tienda real : tiendasReales) {
                if (NitColombia.mismo(t.getNit(), real.getNit())) r.add("la tienda real " + real.getNombre() + " ya tiene el NIT " + t.getNit());
                if (CuentaNegocioService.clave(t.getNombre()) != null && CuentaNegocioService.clave(t.getNombre()).equals(CuentaNegocioService.clave(real.getNombre()))) {
                    r.add("ya hay una tienda real llamada " + real.getNombre());
                }
                if (real.getCorreoContacto() != null && real.getCorreoContacto().equalsIgnoreCase(t.getCorreoContacto())) {
                    r.add("la tienda real " + real.getNombre() + " usa el correo " + t.getCorreoContacto());
                }
            }
        }
        for (Taller t : lote.talleres) {
            for (Taller real : talleresReales) {
                if (NitColombia.mismo(t.getNit(), real.getNit())) r.add("el taller real " + real.getNombre() + " ya tiene el NIT " + t.getNit());
                if (CuentaNegocioService.clave(t.getNombre()) != null && CuentaNegocioService.clave(t.getNombre()).equals(CuentaNegocioService.clave(real.getNombre()))) {
                    r.add("ya hay un taller real llamado " + real.getNombre());
                }
            }
        }
        return r;
    }

    /** "^brainy796\+carlos\.mendoza@gmail\.com$": el texto tal cual, sin que el + o el punto signifiquen otra cosa. */
    static String regexExacto(String texto) {
        StringBuilder s = new StringBuilder("^");
        for (char c : texto.toCharArray()) {
            if ("\\.+*?^$()[]{}|".indexOf(c) >= 0) s.append('\\');
            s.append(c);
        }
        return s.append('$').toString();
    }

    private void escribir(DatosDemoArmado.Lote lote) {
        // 1. Cuentas, negocios y productos (con su stock final)
        mongo.insert(lote.usuarios, Usuario.class);
        mongo.insert(lote.vehiculos, Vehiculo.class);
        mongo.insert(lote.tiendas, Tienda.class);
        mongo.insert(lote.talleres, Taller.class);
        mongo.insert(lote.productos, Producto.class);

        // 2. Los pagos primero: los pedidos, compras y órdenes guardan su id
        List<PagoEntity> guardados = pagos.saveAll(lote.ventas.stream().map(DatosDemoArmado.Venta::pago).toList());
        Map<String, PagoEntity> porReferencia = guardados.stream().collect(Collectors.toMap(PagoEntity::getReferencia, Function.identity()));
        Map<String, CompraCatalogo> compras = lote.compras.stream().collect(Collectors.toMap(CompraCatalogo::getId, Function.identity()));
        Map<String, PedidoTienda> pedidos = lote.pedidos.stream().collect(Collectors.toMap(PedidoTienda::getId, Function.identity()));
        Map<String, OrdenServicio> ordenes = lote.ordenes.stream().collect(Collectors.toMap(OrdenServicio::getId, Function.identity()));
        List<RepartoPago> lineas = new ArrayList<>();
        for (DatosDemoArmado.Venta v : lote.ventas) {
            Long pagoId = porReferencia.get(v.pago().getReferencia()).getId();
            v.lineas().forEach(l -> l.setPagoId(pagoId));
            lineas.addAll(v.lineas());
            if (v.compraId() != null) compras.get(v.compraId()).setPagoId(pagoId);
            v.pedidoIds().forEach(id -> pedidos.get(id).setPagoId(pagoId));
            if (v.esCotizacion()) ordenes.get(v.ordenId()).setPagoCotizacionId(pagoId);
        }

        // 3. Compras, pedidos, movimientos y órdenes
        mongo.insert(lote.compras, CompraCatalogo.class);
        mongo.insert(lote.pedidos, PedidoTienda.class);
        mongo.insert(lote.movimientos, MovimientoInventario.class);
        mongo.insert(lote.ordenes, OrdenServicio.class);
        mongo.insert(lote.cotizaciones, Cotizacion.class);

        // 4. Las líneas de cada negocio y la de la plataforma, que registra ComisionPlataforma con la fecha del pago
        repartos.saveAll(lineas);
        for (DatosDemoArmado.Venta v : lote.ventas) {
            PagoEntity pago = porReferencia.get(v.pago().getReferencia());
            ComisionPlataforma.registrar(repartos, pago, v.baseComision(), parametros.comisionPct(), true, v.lineas());
            List<RepartoPago> plataforma = repartos.findByPagoId(pago.getId()).stream()
                    .filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).toList();
            if (plataforma.isEmpty()) throw new IllegalStateException("El pago " + pago.getReferencia() + " quedó sin su comisión");
            plataforma.forEach(r -> r.setFechaCreacion(pago.getFechaPago()));
            repartos.saveAll(plataforma);
        }

        // 5. Lo que compraron los talleres entra a su inventario, con la fecha en que llegó
        Map<String, Taller> talleres = lote.talleres.stream().collect(Collectors.toMap(Taller::getId, Function.identity()));
        for (DatosDemoArmado.EntradaTaller e : lote.entradasTaller) {
            Taller taller = talleres.get(e.tallerId());
            inventarioTaller.entrarPedido(taller, pedidos.get(e.pedidoId()), taller.getUsuarioId());
            fecharEntrada(e);
        }
        log.info(DatosDemoArmado.resumen(lote));
    }

    /** entrarPedido usa la hora actual: lote, pieza, movimiento y pedido quedan con la fecha de la demo. */
    private void fecharEntrada(DatosDemoArmado.EntradaTaller e) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(e.pedidoId())), new Update().set("fechaEntradaInventario", e.fecha()),
                PedidoTienda.class);
        mongo.updateMulti(Query.query(Criteria.where("tallerId").is(e.tallerId()).and("pedidoId").is(e.pedidoId())),
                new Update().set("fecha", e.fecha()), MovimientoPiezaTaller.class);
        for (PiezaTaller pieza : mongo.find(Query.query(Criteria.where("tallerId").is(e.tallerId())), PiezaTaller.class)) {
            boolean cambio = false;
            for (PiezaTaller.Lote l : pieza.getLotes()) {
                if (e.pedidoId().equals(l.getPedidoId())) {
                    l.setFechaEntrada(e.fecha());
                    cambio = true;
                }
            }
            if (!cambio) continue;
            if (pieza.getFechaCreacion() == null || pieza.getFechaCreacion().isAfter(e.fecha())) pieza.setFechaCreacion(e.fecha());
            mongo.save(pieza);
        }
    }

    // ─── Borrar ──────────────────────────────────────────────────────────────

    /** Lo que borró y lo que encontró sin borrar (por llevar dinero real). */
    public record Borrado(long repartos, long pagos, Map<String, Long> mongo, List<String> avisos) {
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder("Datos demo borrados: ").append(pagos).append(" pagos y ").append(repartos)
                    .append(" líneas de reparto en Postgres");
            mongo.forEach((c, n) -> { if (n > 0) s.append("; ").append(c).append(": ").append(n); });
            avisos.forEach(a -> s.append("\nAVISO: ").append(a));
            return s.toString();
        }
    }

    /** Borra solo lo demo. Se puede correr las veces que haga falta: sin datos demo no hace nada. */
    public Borrado borrar() {
        List<String> avisos = avisosSinBorrar();
        // Postgres: primero las líneas (apuntan a los pagos), después los pagos
        long lineas = repartos.borrarRepartosDemo();
        long pagosBorrados = pagos.borrarPagosDemo();
        Map<String, Long> mongoBorrado = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : COLECCIONES_POR_DUENO.entrySet()) {
            mongoBorrado.merge(e.getKey(), mongo.remove(demoPor(e.getValue()), e.getKey()).getDeletedCount(), Long::sum);
        }
        // La marca, al final: si algo falla antes, la siembra siguiente sabe que hay restos
        for (String c : COLECCIONES_POR_ID) {
            mongoBorrado.merge(c, mongo.remove(demoPorId(), c).getDeletedCount(), Long::sum);
        }
        Borrado b = new Borrado(lineas, pagosBorrados, mongoBorrado, avisos);
        log.info(b.toString());
        return b;
    }

    /** Lo real que toca algo demo: no se borra (puede haber dinero de verdad), pero se avisa para revisarlo a mano. */
    List<String> avisosSinBorrar() {
        List<String> avisos = new ArrayList<>();
        long pedidosReales = mongo.count(Query.query(new Criteria().andOperator(Criteria.where("tiendaId").regex(ES_DEMO),
                Criteria.where("_id").not().regex(ES_DEMO))), PedidoTienda.class);
        if (pedidosReales > 0) avisos.add(pedidosReales + " pedido(s) reales a tiendas demo (no se borran: revísalos y sus pagos)");
        long ordenesReales = mongo.count(Query.query(new Criteria().andOperator(
                new Criteria().orOperator(Criteria.where("usuarioId").regex(ES_DEMO), Criteria.where("tallerId").regex(ES_DEMO)),
                Criteria.where("_id").not().regex(ES_DEMO))), OrdenServicio.class);
        if (ordenesReales > 0) avisos.add(ordenesReales + " orden(es) reales de clientes o talleres demo (no se borran)");
        long lineasReales = repartos.contarRepartosRealesDeNegociosDemo();
        if (lineasReales > 0) avisos.add(lineasReales + " línea(s) de pagos reales a negocios demo (no se borran)");
        return avisos;
    }
}
