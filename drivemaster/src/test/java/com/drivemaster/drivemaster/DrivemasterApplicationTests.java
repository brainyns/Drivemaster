package com.drivemaster.drivemaster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo de Spring: se conecta a MongoDB y a Postgres, corre las migraciones y los seeders del
 * arranque y deja activas las tareas programadas (que mandan correos). Por eso solo corre cuando se pide a propósito
 * con DRIVEMASTER_IT=true y las variables de unas bases DE PRUEBA, nunca las de producción:
 *
 * <pre>
 * DRIVEMASTER_IT=true SPRING_DATA_MONGODB_URI=... DB_URL=jdbc:postgresql://... DB_USERNAME=... DB_PASSWORD=... \
 *   ./mvnw test -Dtest=DrivemasterApplicationTests
 * </pre>
 *
 * Sin DRIVEMASTER_IT, ./mvnw test la marca como omitida (sin ella, Spring no lee el .env y el contexto no arranca).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DRIVEMASTER_IT", matches = "true")
class DrivemasterApplicationTests {

	@Test
	void contextLoads() {
	}

}
