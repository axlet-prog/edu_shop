package com.example.edu_shop.exporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExporterApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExporterApplication.class, args);
	}

}
