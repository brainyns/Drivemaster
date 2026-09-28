package com.drivemaster.drivemaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrivemasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(DrivemasterApplication.class, args);
	}

}
   