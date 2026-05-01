package com.betterreads.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI betterReadsOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("BetterReads API")
                .version("v1")
                .description("Book tracking and recommendation API.")
                .contact(new Contact().name("BetterReads"))
                .license(new License().name("Apache 2.0")));
    }
}
