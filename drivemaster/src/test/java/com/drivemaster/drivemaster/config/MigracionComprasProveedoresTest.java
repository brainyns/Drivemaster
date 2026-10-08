package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.config.MigracionComprasProveedores.Resultado;
import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.testutil.LogsCapturados;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** La migración no corre sola, es idempotente, no borra nada y reporta cuántos registros asignó. */
class MigracionComprasProveedoresTest {

    private MongoTemplate mongo;
    // Registros sin dueño que "hay" en la base; se vacían al asignarlos (como haría Mongo)
    private final AtomicLong comprasSinDueno = new AtomicLong(7);
    private final AtomicLong proveedoresSinDueno = new AtomicLong(3);

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        when(mongo.count(any(Query.class), eq(Compra.class))).thenAnswer(i -> comprasSinDueno.get());
        when(mongo.count(any(Query.class), eq(Proveedor.class))).thenAnswer(i -> proveedoresSinDueno.get());
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Compra.class)))
                .thenAnswer(i -> UpdateResult.acknowledged(comprasSinDueno.get(), comprasSinDueno.getAndSet(0), null));
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Proveedor.class)))
                .thenAnswer(i -> UpdateResult.acknowledged(proveedoresSinDueno.get(), proveedoresSinDueno.getAndSet(0), null));
        Tienda destino = Tienda.builder().id("t-heredera").nombre("Repuestos Norte").build();
        when(mongo.findById("t-heredera", Tienda.class)).thenReturn(destino);
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(destino));
    }

    private MigracionComprasProveedores migracion(String tienda, boolean simular) {
        return new MigracionComprasProveedores(mongo, tienda, simular);
    }

    @Test
    void asignaLosRegistrosSinDuenoYReportaCuantos() {
        Resultado r = migracion("t-heredera", false).ejecutar();

        assertEquals(7, r.comprasAsignadas());
        assertEquals(3, r.proveedoresAsignados());
        assertEquals(0, r.comprasSinDuenoDespues(), "no queda ninguno sin dueño");
        assertEquals(0, r.proveedoresSinDuenoDespues());
        assertEquals("t-heredera", r.tiendaId());

        ArgumentCaptor<Update> cambio = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(any(Query.class), cambio.capture(), eq(Compra.class));
        assertEquals("{\"$set\": {\"tiendaId\": \"t-heredera\"}}", cambio.getValue().getUpdateObject().toJson(), "solo se escribe tiendaId");
    }

    @Test
    void soloTocaLosQueNoTienenDuenoYNuncaBorraNada() {
        migracion("t-heredera", false).ejecutar();

        ArgumentCaptor<Query> filtro = ArgumentCaptor.forClass(Query.class);
        verify(mongo, atLeastOnce()).updateMulti(filtro.capture(), any(Update.class), any(Class.class));
        for (Query q : filtro.getAllValues()) {
            assertEquals("{\"tiendaId\": null}", q.getQueryObject().toJson(), "solo registros sin tiendaId");
        }
        verify(mongo, never()).remove(any(Query.class), any(Class.class));
        verify(mongo, never()).remove(any(Object.class));
        verify(mongo, never()).dropCollection(any(Class.class));
        verify(mongo, never()).save(any());
    }

    @Test
    void esIdempotenteUnaSegundaCorridaAsignaCero() {
        migracion("t-heredera", false).ejecutar();
        Resultado segunda = migracion("t-heredera", false).ejecutar();

        assertEquals(0, segunda.comprasAsignadas());
        assertEquals(0, segunda.proveedoresAsignados());
        assertEquals(0, segunda.comprasSinDuenoDespues());
    }

    @Test
    void laSimulacionCuentaPeroNoEscribeNada() {
        Resultado r = migracion("t-heredera", true).ejecutar();

        assertTrue(r.simulado());
        assertEquals(7, r.comprasAsignadas());
        assertEquals(3, r.proveedoresAsignados());
        assertEquals(7, r.comprasSinDuenoDespues(), "sigue igual: no se escribió");
        verify(mongo, never()).updateMulti(any(Query.class), any(Update.class), any(Class.class));
    }

    @Test
    void laTiendaSeEligePorNombreExactoSinMayusculasONoSeTocaNada() {
        assertEquals("t-heredera", migracion("repuestos norte", false).ejecutar().tiendaId());
    }

    @Test
    void conTiendaInexistenteAmbiguaOSinIndicarAbortaSinTocarNada() {
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> migracion("No existe", false).ejecutar());

        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(
                Tienda.builder().id("x1").nombre("Igual").build(), Tienda.builder().id("x2").nombre("Igual").build()));
        IllegalStateException ambigua = assertThrows(IllegalStateException.class, () -> migracion("Igual", false).ejecutar());
        assertTrue(ambigua.getMessage().contains("2 tiendas"));

        assertThrows(IllegalStateException.class, () -> migracion("", false).ejecutar());
        assertThrows(IllegalStateException.class, () -> migracion(null, false).ejecutar());

        verify(mongo, never()).updateMulti(any(Query.class), any(Update.class), any(Class.class));
    }

    @Test
    void elReporteQuedaEnElLogConLaCantidadAsignada() {
        List<String> logs = LogsCapturados.de(() -> migracion("t-heredera", false).ejecutar());

        String todo = String.join("\n", logs);
        assertTrue(todo.contains("7 compra(s)") && todo.contains("3 proveedor(es)") && todo.contains("Repuestos Norte"), todo);
    }

    // ─── No corre sola ───────────────────────────────────────────────────────

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withBean(MongoTemplate.class, () -> mongo)
                .withUserConfiguration(MigracionComprasProveedores.class);
    }

    @Test
    void sinElIndicadorExplicitoLaMigracionNiSiquieraExiste() {
        contexto().run(ctx -> assertFalse(ctx.containsBean("migracionComprasProveedores")));
        contexto().withPropertyValues("migracion.compras-proveedores.ejecutar=false",
                "migracion.compras-proveedores.tienda=t-heredera")
                .run(ctx -> assertFalse(ctx.containsBean("migracionComprasProveedores")));
        verify(mongo, never()).updateMulti(any(Query.class), any(Update.class), any(Class.class));
    }

    @Test
    void conElIndicadorYLaTiendaCorreAlArrancar() {
        contexto().withPropertyValues("migracion.compras-proveedores.ejecutar=true",
                "migracion.compras-proveedores.tienda=t-heredera")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertTrue(ctx.containsBean("migracionComprasProveedores"));
                    // Spring Boot ejecuta los ApplicationRunner al arrancar; aquí se invoca como lo haría él
                    ctx.getBean(org.springframework.boot.ApplicationRunner.class).run(null);
                });
        verify(mongo, times(1)).updateMulti(any(Query.class), any(Update.class), eq(Compra.class));
    }

    @Test
    void elIndicadorViveApagadoPorDefectoEnLaConfiguracion() throws Exception {
        var props = new java.util.Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        assertEquals("${MIGRAR_COMPRAS_PROVEEDORES:false}", props.getProperty("migracion.compras-proveedores.ejecutar"));
        assertEquals("${MIGRAR_COMPRAS_PROVEEDORES_SIMULAR:false}", props.getProperty("migracion.compras-proveedores.simular"));
    }
}
