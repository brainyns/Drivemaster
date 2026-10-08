package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Tienda;
import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Asigna a una tienda las compras y los proveedores que todavía no tienen dueño (los de antes de que cada tienda
 * llevara los suyos).
 *
 * NO corre sola: solo existe cuando se pide con MIGRAR_COMPRAS_PROVEEDORES=true, y necesita
 * MIGRAR_COMPRAS_PROVEEDORES_TIENDA (el id o el nombre exacto de la tienda destino). Con
 * MIGRAR_COMPRAS_PROVEEDORES_SIMULAR=true solo cuenta lo que asignaría, sin escribir nada.
 *
 * Es idempotente (solo toca registros sin dueño: una segunda corrida asigna 0), no borra ni modifica nada más
 * que el campo tiendaId, y reporta cuántos registros asignó. Si la tienda no existe o el nombre es ambiguo, aborta
 * el arranque sin tocar nada.
 */
@Component
@ConditionalOnProperty(name = "migracion.compras-proveedores.ejecutar", havingValue = "true")
public class MigracionComprasProveedores implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracionComprasProveedores.class);

    public record Resultado(String tiendaId, String tiendaNombre, boolean simulado,
                            long comprasAsignadas, long proveedoresAsignados,
                            long comprasSinDuenoDespues, long proveedoresSinDuenoDespues) {}

    private final MongoTemplate mongo;
    private final String destino;
    private final boolean simular;

    public MigracionComprasProveedores(MongoTemplate mongo,
                                       @Value("${migracion.compras-proveedores.tienda:}") String destino,
                                       @Value("${migracion.compras-proveedores.simular:false}") boolean simular) {
        this.mongo = mongo;
        this.destino = destino;
        this.simular = simular;
    }

    @Override
    public void run(ApplicationArguments args) {
        ejecutar();
    }

    public Resultado ejecutar() {
        Tienda tienda = resolverTienda();
        Query sinDueno = Query.query(Criteria.where("tiendaId").is(null)); // null o campo ausente
        long compras;
        long proveedores;
        if (simular) {
            compras = mongo.count(sinDueno, Compra.class);
            proveedores = mongo.count(sinDueno, Proveedor.class);
        } else {
            Update asignar = new Update().set("tiendaId", tienda.getId());
            UpdateResult c = mongo.updateMulti(sinDueno, asignar, Compra.class);
            UpdateResult p = mongo.updateMulti(sinDueno, asignar, Proveedor.class);
            compras = c.getModifiedCount();
            proveedores = p.getModifiedCount();
        }
        long comprasRestantes = mongo.count(sinDueno, Compra.class);
        long proveedoresRestantes = mongo.count(sinDueno, Proveedor.class);
        Resultado r = new Resultado(tienda.getId(), tienda.getNombre(), simular, compras, proveedores,
                comprasRestantes, proveedoresRestantes);
        log.info("Migración de compras y proveedores {}hacia la tienda \"{}\" ({}): {} compra(s) y {} proveedor(es) {}. "
                        + "Sin dueño después: {} compra(s) y {} proveedor(es).",
                simular ? "(SIMULACIÓN, no se escribió nada) " : "", tienda.getNombre(), tienda.getId(),
                compras, proveedores, simular ? "se asignarían" : "asignados", comprasRestantes, proveedoresRestantes);
        return r;
    }

    private Tienda resolverTienda() {
        if (destino == null || destino.isBlank()) {
            throw new IllegalStateException("MIGRAR_COMPRAS_PROVEEDORES_TIENDA no está definida: indica el id o el nombre exacto de la tienda destino.");
        }
        String buscado = destino.trim();
        Tienda porId = mongo.findById(buscado, Tienda.class);
        if (porId != null) return porId;
        List<Tienda> porNombre = mongo.find(Query.query(Criteria.where("nombre").regex("^" + java.util.regex.Pattern.quote(buscado) + "$", "i")), Tienda.class);
        if (porNombre.size() == 1) return porNombre.get(0);
        throw new IllegalStateException(porNombre.isEmpty()
                ? "No existe una tienda con ese id o nombre exacto; no se tocó nada."
                : "Hay " + porNombre.size() + " tiendas con ese nombre; usa el id para elegir una. No se tocó nada.");
    }
}
