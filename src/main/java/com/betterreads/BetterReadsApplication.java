package com.betterreads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public final class BetterReadsApplication {

    private BetterReadsApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(BetterReadsApplication.class, args);
    }

}
