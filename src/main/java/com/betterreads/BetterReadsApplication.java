package com.betterreads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public final class BetterReadsApplication {

    private BetterReadsApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(BetterReadsApplication.class, args);
    }

}
