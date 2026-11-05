package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.repository.MovimientoPiezaTallerRepository;
import com.drivemaster.drivemaster.repository.PiezaTallerRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo que un doble en memoria no puede demostrar: que en Mongo real @Version frena dos ventas simultáneas de las últimas
 * unidades y que el id determinista frena una reversa repetida. Usa una base temporal propia, nunca una existente, y se
 * salta si no hay un mongod local.
 */
class StockTallerMongoRealTest {

    private MongoClient cliente;
    private MongoTemplate mongo;
    private String base;
    private StockTallerService stock;
    private PiezaTallerRepository piezas;

    @BeforeEach
    void setUp() {
        cliente = MongoClients.create("mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=800");
        try {
            cliente.getDatabase("admin").runCommand(new Document("ping", 1));
        } catch (RuntimeException e) {
            cliente.close();
            cliente = null;
            Assumptions.assumeTrue(false, "Sin mongod local");
        }
        base = "dm_prueba_stock_" + UUID.randomUUID().toString().replace("-", "");
        mongo = new MongoTemplate(cliente, base);
        MongoRepositoryFactory fabrica = new MongoRepositoryFactory(mongo);
        piezas = fabrica.getRepository(PiezaTallerRepository.class);
        stock = new StockTallerService(piezas, fabrica.getRepository(MovimientoPiezaTallerRepository.class));
    }

    @AfterEach
    void limpiar() {
        if (cliente == null) return;
        cliente.getDatabase(base).drop();
        cliente.close();
    }

    private String piezaConStock(int cantidad) {
        stock.entrar(new StockTallerService.Entrada("t1", PiezaTaller.builder().clave("REF:X").nombre("Filtro").build(),
                PiezaTaller.Lote.builder().id("ped|p").pedidoId("ped").productoId("p").cantidad(cantidad).disponible(cantidad)
                        .costoUnitario(1000.0).fechaEntrada(LocalDateTime.now()).build(), "u"));
        return PiezaTaller.idDe("t1", "REF:X");
    }

    @Test
    void de_veinte_compras_simultaneas_de_tres_unidades_solo_tres_se_venden() throws Exception {
        String pieza = piezaConStock(3);
        ExecutorService hilos = Executors.newFixedThreadPool(20);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String referencia = "PAGO-" + i;
            Callable<Boolean> venta = () -> {
                try {
                    stock.descontar(new StockTallerService.Venta("t1", pieza, 1, referencia, 5000, "o", "c", 1L, "u"));
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            };
            resultados.add(hilos.submit(venta));
        }
        int vendidas = 0;
        for (Future<Boolean> r : resultados) if (r.get()) vendidas++;
        hilos.shutdown();

        PiezaTaller final_ = piezas.findById(pieza).orElseThrow();
        assertEquals(3, vendidas);
        assertEquals(0, final_.getStock());
        assertEquals(0, final_.getLotes().get(0).getDisponible());
        assertEquals(3, mongo.count(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("tipo").is(MovimientoPiezaTaller.SALIDA)),
                MovimientoPiezaTaller.class));
    }

    @Test
    void la_reversa_repetida_a_la_vez_suma_una_sola_vez() throws Exception {
        String pieza = piezaConStock(3);
        stock.descontar(new StockTallerService.Venta("t1", pieza, 2, "PAGO-1", 5000, "o", "c", 1L, "u"));

        ExecutorService hilos = Executors.newFixedThreadPool(6);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 6; i++) resultados.add(hilos.submit(() -> stock.revertir("PAGO-1", "cancelada", "admin")));
        int revertidas = 0;
        for (Future<Integer> r : resultados) revertidas += r.get();
        hilos.shutdown();

        assertEquals(1, revertidas);
        assertEquals(3, piezas.findById(pieza).orElseThrow().getStock());
    }

    @Test
    void el_lote_y_su_rastro_se_guardan_y_se_leen_igual() {
        String pieza = piezaConStock(3);
        var salida = stock.descontar(new StockTallerService.Venta("t1", pieza, 1, "PAGO-1", 5000, "o", "c", 1L, "u"));
        var leida = mongo.findById(salida.getId(), MovimientoPiezaTaller.class);
        assertNotNull(leida);
        assertEquals("ped|p", leida.getLotes().get(0).getLoteId());
        assertEquals(1000.0, leida.getLotes().get(0).getCostoUnitario());
    }
}
