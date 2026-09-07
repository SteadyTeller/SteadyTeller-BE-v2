package com.steadyteller.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class SteadyTellerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteadyTellerApplication.class, args);
    }

}
