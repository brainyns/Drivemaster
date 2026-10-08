package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Venta;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Limpieza de la antigua "Tienda DriveMaster" (DriveMaster es la aplicación, no una tienda).
 *
 * NO corre sola: solo existe con MIGRAR_LIMPIEZA_TIENDA_PROPIA=true. Con MIGRAR_LIMPIEZA_TIENDA_PROPIA_SIMULAR=true
 * solo cuenta y escribe el informe en el log, sin borrar nada (es el "informe previo").
 *
 * Qué borra (y nada más): los productos de esa tienda, sus movimientos de inventario y el registro de la tienda.
 * NO toca ventas, solicitudes, cotizaciones ni compras: se conservan como historial (guardan su propia copia del
 * nombre y del precio de cada línea).
 *
 * Es idempotente: si ya no hay tienda propia no hace nada. Antes de borrar comprueba que todo cuadre y, si algo no
 * cuadra, aborta sin tocar nada. Aborta si:
 *  - hay más de una tienda marcada como propia, o la que hay tiene una cuenta de usuario (no es la tienda sin dueño);
 *  - la tienda tiene pedidos, compras del catálogo, compras a proveedores o proveedores propios;
 *  - hay ventas o solicitudes sin cerrar, o cotizaciones de taller sin resolver, con productos de esa tienda:
 *    al pagarse o aprobarse buscarían un producto que ya no existe. Hay que cerrarlas antes.
 */
@Component
@Order(40)
@ConditionalOnProperty(name = "migracion.limpieza-tienda-propia.ejecutar", havingValue = "true")
public class LimpiezaTiendaPropia implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaTiendaPropia.class);

    static final List<String> VENTAS_ABIERTAS = List.of("PENDIENTE", "PENDIENTE_PAGO", "PENDIENTE_APROBACION", "APROBADO");
    static final List<String> SOLICITUDES_ABIERTAS = List.of("PENDIENTE", "PENDIENTE_PAGO", "PENDIENTE_APROBACION", "APROBADO", "PAGO_VERIFICADO");
    static final List<String> COTIZACIONES_ABIERTAS = List.of(Cotizacion.EMITIDA, Cotizacion.APROBADA, Cotizacion.CAMBIOS_SOLICITADOS);

    public record Informe(boolean tiendaEncontrada, String tiendaId, String tiendaNombre, boolean simulado,
                          long productos, long productosSinTienda, long movimientos, long movimientosFueraDeLaTienda,
                          long ventas, long ventasAbiertas, long solicitudes, long solicitudesAbiertas,
                          long cotizaciones, long cotizacionesAbiertas,
                          long pedidos, long comprasCatalogo, long comprasAProveedores, long proveedores,
                          List<String> bloqueos,
                          long productosBorrados, long movimientosBorrados, long tiendasBorradas) {}

    private final MongoTemplate mongo;
    private final boolean simular;

    public LimpiezaTiendaPropia(MongoTemplate mongo, @Value("${migracion.limpieza-tienda-propia.simular:false}") boolean simular) {
        this.mongo = mongo;
        this.simular = simular;
    }

    @Override
    public void run(ApplicationArguments args) {
        ejecutar();
    }

    public Informe ejecutar() {
        List<Document> propias = mongo.find(Query.query(Criteria.where("plataforma").is(true)), Document.class, "tiendas");
        if (propias.isEmpty()) {
            log.info("Limpieza de la tienda propia: no hay ninguna tienda marcada como propia; no hay nada que hacer.");
            return new Informe(false, null, null, simular, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, 0);
        }
        if (propias.size() > 1) {
            throw new IllegalStateException("Hay " + propias.size() + " tiendas marcadas como propias: no se sabe cuál limpiar. No se tocó nada.");
        }
        Document tienda = propias.get(0);
        String id = String.valueOf(tienda.get("_id"));
        String nombre = tienda.getString("nombre");

        List<String> bloqueos = new ArrayList<>();
        Object usuario = tienda.get("usuarioId");
        if (usuario != null && !String.valueOf(usuario).isBlank()) {
            bloqueos.add("la tienda \"" + nombre + "\" tiene una cuenta de usuario: no es la tienda propia sin dueño");
        }

        // Productos de la tienda
        List<String> productoIds = mongo.find(Query.query(Criteria.where("tiendaId").is(id)), Producto.class).stream()
                .map(Producto::getId).toList();
        long productos = productoIds.size();
        long productosSinTienda = mongo.count(Query.query(Criteria.where("tiendaId").is(null)), Producto.class);

        // Movimientos: los de la tienda (se borran) y los que apuntan a sus productos pero sin la tienda (no se tocan)
        long movimientos = mongo.count(Query.query(Criteria.where("tiendaId").is(id)), MovimientoInventario.class);
        long movimientosFuera = productoIds.isEmpty() ? 0 : mongo.count(Query.query(
                Criteria.where("productoId").in(productoIds).and("tiendaId").ne(id)), MovimientoInventario.class);

        // Historial que se conserva: ventas, solicitudes y cotizaciones con productos de la tienda
        Criteria ventasConProducto = Criteria.where("productos.productoId").in(productoIds);
        long ventas = productoIds.isEmpty() ? 0 : mongo.count(Query.query(ventasConProducto), Venta.class);
        long ventasAbiertas = productoIds.isEmpty() ? 0 : mongo.count(Query.query(
                Criteria.where("productos.productoId").in(productoIds).and("estado").in(VENTAS_ABIERTAS)), Venta.class);
        long solicitudes = productoIds.isEmpty() ? 0 : mongo.count(Query.query(Criteria.where("productos.productoId").in(productoIds)), Solicitud.class);
        long solicitudesAbiertas = productoIds.isEmpty() ? 0 : mongo.count(Query.query(
                Criteria.where("productos.productoId").in(productoIds).and("estado").in(SOLICITUDES_ABIERTAS)), Solicitud.class);
        long cotizaciones = productoIds.isEmpty() ? 0 : mongo.count(Query.query(Criteria.where("items.productoId").in(productoIds)), Cotizacion.class);
        long cotizacionesAbiertas = productoIds.isEmpty() ? 0 : mongo.count(Query.query(
                Criteria.where("items.productoId").in(productoIds).and("estado").in(COTIZACIONES_ABIERTAS)), Cotizacion.class);

        // Dinero y cuentas del marketplace atados a la tienda: si existen, no se puede borrar
        long pedidos = mongo.count(Query.query(Criteria.where("tiendaId").is(id)), PedidoTienda.class);
        long comprasCatalogo = mongo.count(Query.query(Criteria.where("lineas.tiendaId").is(id)), CompraCatalogo.class);
        long comprasAProveedores = mongo.count(Query.query(Criteria.where("tiendaId").is(id)), Compra.class);
        long proveedores = mongo.count(Query.query(Criteria.where("tiendaId").is(id)), Proveedor.class);

        if (pedidos > 0) bloqueos.add(pedidos + " pedido(s) de tienda con esta tienda (hay dinero repartido a ella)");
        if (comprasCatalogo > 0) bloqueos.add(comprasCatalogo + " compra(s) del catálogo con productos de esta tienda");
        if (comprasAProveedores > 0) bloqueos.add(comprasAProveedores + " compra(s) a proveedores asignada(s) a esta tienda");
        if (proveedores > 0) bloqueos.add(proveedores + " proveedor(es) asignado(s) a esta tienda");
        if (ventasAbiertas > 0) bloqueos.add(ventasAbiertas + " venta(s) sin cerrar con productos de esta tienda (ciérralas o anúlalas antes)");
        if (solicitudesAbiertas > 0) bloqueos.add(solicitudesAbiertas + " solicitud(es) sin cerrar con productos de esta tienda (apruébalas, recházalas o espera a que se paguen)");
        if (cotizacionesAbiertas > 0) bloqueos.add(cotizacionesAbiertas + " cotización(es) de taller sin resolver con productos de esta tienda");

        long productosBorrados = 0;
        long movimientosBorrados = 0;
        long tiendasBorradas = 0;
        if (!bloqueos.isEmpty() && !simular) {
            String motivo = String.join("; ", bloqueos);
            log.error("Limpieza de la tienda propia ABORTADA, no se tocó nada: {}", motivo);
            throw new IllegalStateException("La limpieza no cuadra y se abortó sin tocar nada: " + motivo);
        }
        if (!simular) {
            // Primero los movimientos y los productos, al final la tienda: si algo se interrumpe, volver a correr continúa
            movimientosBorrados = mongo.remove(Query.query(Criteria.where("tiendaId").is(id)), MovimientoInventario.class).getDeletedCount();
            productosBorrados = mongo.remove(Query.query(Criteria.where("tiendaId").is(id)), Producto.class).getDeletedCount();
            tiendasBorradas = mongo.remove(Query.query(Criteria.where("_id").is(id).and("plataforma").is(true)), "tiendas").getDeletedCount();
        }

        Informe r = new Informe(true, id, nombre, simular, productos, productosSinTienda, movimientos, movimientosFuera,
                ventas, ventasAbiertas, solicitudes, solicitudesAbiertas, cotizaciones, cotizacionesAbiertas,
                pedidos, comprasCatalogo, comprasAProveedores, proveedores, List.copyOf(bloqueos),
                productosBorrados, movimientosBorrados, tiendasBorradas);
        escribir(r);
        return r;
    }

    private static void escribir(Informe r) {
        String cabecera = r.simulado() ? "INFORME PREVIO (simulación: no se borró nada)" : "LIMPIEZA EJECUTADA";
        log.info("""
                Limpieza de la tienda propia "{}" ({}) · {}
                  Productos de la tienda ............ {}
                  Movimientos de inventario ......... {}
                  Productos sin tienda (no se tocan)  {}
                  Movimientos de sus productos sin la tienda (no se tocan) {}
                  Historial que SE CONSERVA: ventas {} ({} sin cerrar) · solicitudes {} ({} sin cerrar) · cotizaciones {} ({} sin resolver)
                  Atados a la tienda: pedidos {} · compras del catálogo {} · compras a proveedores {} · proveedores {}
                  {}""",
                r.tiendaNombre(), r.tiendaId(), cabecera, r.productos(), r.movimientos(), r.productosSinTienda(),
                r.movimientosFueraDeLaTienda(), r.ventas(), r.ventasAbiertas(), r.solicitudes(), r.solicitudesAbiertas(),
                r.cotizaciones(), r.cotizacionesAbiertas(), r.pedidos(), r.comprasCatalogo(), r.comprasAProveedores(), r.proveedores(),
                r.simulado()
                        ? (r.bloqueos().isEmpty() ? "Todo cuadra: se podría ejecutar. Se borrarían " + r.productos() + " producto(s), "
                                + r.movimientos() + " movimiento(s) y la tienda."
                                : "NO se podría ejecutar todavía: " + String.join("; ", r.bloqueos()))
                        : "Borrados: " + r.productosBorrados() + " producto(s), " + r.movimientosBorrados() + " movimiento(s), "
                                + r.tiendasBorradas() + " tienda(s).");
        if (!r.simulado() && (r.productosBorrados() != r.productos() || r.movimientosBorrados() != r.movimientos() || r.tiendasBorradas() != 1)) {
            log.warn("Lo borrado no coincide con lo contado (cambió algo mientras corría). Vuelve a correr la limpieza para terminar.");
        }
    }
}
