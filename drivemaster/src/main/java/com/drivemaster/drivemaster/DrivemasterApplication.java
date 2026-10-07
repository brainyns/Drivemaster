package com.drivemaster.drivemaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class DrivemasterApplication {

	public static void main(String[] args) {
		// Todas las fechas del negocio (citas, plazos, correos) son hora de Colombia.
		// Sin esto, en Render (UTC) LocalDateTime.now() iba 5 horas adelantado frente
		// a las fechas que escribe el usuario.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
		SpringApplication.run(DrivemasterApplication.class, args);
	}

}
   