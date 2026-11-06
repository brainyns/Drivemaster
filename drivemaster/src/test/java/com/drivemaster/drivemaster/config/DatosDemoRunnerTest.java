package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.service.DatosDemoService;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** El runner solo actúa con el perfil demo-seed y con el nombre exacto de la base confirmado. */
class DatosDemoRunnerTest {

    private final DatosDemoService demo = mock(DatosDemoService.class);

    private DatosDemoRunner runner(String accion, String confirmar) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(db.getName()).thenReturn("drivemasterDB");
        when(mongo.getDb()).thenReturn(db);
        return new DatosDemoRunner(demo, mongo, mock(ConfigurableApplicationContext.class), accion, confirmar,
                "jdbc:postgresql://postgres.abc:clave@aws-0.pooler.supabase.com:6543/postgres?password=secreta");
    }

    @Test
    void solo_existe_con_el_perfil_demo_seed() {
        assertArrayEquals(new String[]{"demo-seed"}, DatosDemoRunner.class.getAnnotation(Profile.class).value());
    }

    @Test
    void sin_confirmar_la_base_exacta_no_hace_nada() {
        for (String malo : new String[]{"", "drivemasterdb", "drivemasterDB-prueba", "otraBase"}) {
            assertEquals(2, runner("sembrar", malo).ejecutar(), malo);
            assertEquals(2, runner("borrar", malo).ejecutar(), malo);
        }
        verifyNoInteractions(demo);
    }

    @Test
    void con_la_base_confirmada_ejecuta_la_accion() {
        when(demo.sembrar()).thenReturn("ok");
        assertEquals(0, runner("sembrar", "drivemasterDB").ejecutar());
        verify(demo).sembrar();
        when(demo.borrar()).thenReturn(new DatosDemoService.Borrado(0, 0, java.util.Map.of(), java.util.List.of()));
        assertEquals(0, runner(" BORRAR ", "drivemasterDB").ejecutar());
        verify(demo).borrar();
    }

    @Test
    void una_accion_desconocida_o_una_falla_no_terminan_en_cero() {
        assertEquals(2, runner("limpiar", "drivemasterDB").ejecutar());
        when(demo.sembrar()).thenThrow(new IllegalStateException("choque"));
        assertEquals(1, runner("sembrar", "drivemasterDB").ejecutar());
    }

    @Test
    void el_log_no_muestra_usuario_ni_clave_de_postgres() {
        String limpio = DatosDemoRunner.sinCredenciales("jdbc:postgresql://usuario:clave@host:5432/db?password=secreta&ssl=true");
        assertFalse(limpio.contains("clave") || limpio.contains("secreta") || limpio.contains("usuario"), limpio);
    }
}
