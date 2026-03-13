package com.deployment.Git2Go;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@org.springframework.data.jpa.repository.config.EnableJpaAuditing
public class Git2GoApplication {

	public static void main(String[] args) {
		SpringApplication.run(Git2GoApplication.class, args);
	}

}
