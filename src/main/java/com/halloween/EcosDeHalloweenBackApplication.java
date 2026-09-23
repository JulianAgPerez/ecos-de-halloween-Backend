package com.halloween;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcosDeHalloweenBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcosDeHalloweenBackApplication.class, args);
	}

}
