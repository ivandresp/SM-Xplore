package com.smxplore.securityreports;

import org.springframework.boot.SpringApplication;

public class TestSecurityReportsApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecurityReportsApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
