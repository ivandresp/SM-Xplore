package com.smxplore.securityreports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecurityReportsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityReportsApplication.class, args);
    }
}
