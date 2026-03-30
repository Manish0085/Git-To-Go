package com.git2go.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Scheduled jobs enable — log cleanup, future health checks
public class SingleClickDeploymentSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SingleClickDeploymentSystemApplication.class, args);
	}

}
