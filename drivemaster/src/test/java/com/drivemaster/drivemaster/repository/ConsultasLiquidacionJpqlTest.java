package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Las consultas @Query de liquidación y de pagos son JPQL que Spring solo valida al
 * arrancar con la base de datos. Aquí se compilan con el parser real de Hibernate (sin
 * conexión), para que un error de sintaxis o un campo mal escrito falle en las pruebas
 * y no al desplegar.
 */
class ConsultasLiquidacionJpqlTest {

    private static StandardServiceRegistry registro;
    private static SessionFactory fabrica;

    @BeforeAll
    static void crearFabrica() {
        registro = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        fabrica = new MetadataSources(registro)
                .addAnnotatedClass(RepartoPago.class)
                .addAnnotatedClass(Reembolso.class)
                .addAnnotatedClass(PagoEntity.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void cerrar() {
        if (fabrica != null) fabrica.close();
    }

    private static void compilar(Class<?> repositorio) {
        int consultas = 0;
        try (Session s = fabrica.openSession()) {
            for (Method m : repositorio.getMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q == null) continue;
                consultas++;
                assertDoesNotThrow(() -> {
                    if (q.value().trim().toLowerCase().startsWith("select")) s.createSelectionQuery(q.value());
                    else s.createMutationQuery(q.value());
                }, repositorio.getSimpleName() + "." + m.getName() + ": " + q.value());
            }
        }
        assertNotNull(consultas);
    }

    @Test
    void lasConsultasDeRepartosSonValidas() {
        compilar(RepartoPagoRepository.class);
    }

    @Test
    void lasConsultasDeReembolsosSonValidas() {
        compilar(ReembolsoRepository.class);
    }

    @Test
    void lasConsultasDePagosSonValidas() {
        compilar(PagoRepository.class);
    }
}
