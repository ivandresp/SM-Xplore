package com.smxplore.sitesandservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SitesAndServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SitesAndServicesApplication.class, args);
    }
}
