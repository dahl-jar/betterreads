package com.betterreads.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for {@code /v3/api-docs} and Swagger UI.
 * the bearer-JWT scheme gives Swagger UI its Authorize button, public endpoints opt out with
 * {@code @SecurityRequirements}
 */
@Configuration
class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearer-jwt";

    private final String publicUrl;

    OpenApiConfig(@Value("${springdoc.public-url:http://localhost:8080}") final String publicUrl) {
        this.publicUrl = publicUrl;
    }

    @Bean
    OpenAPI betterReadsOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("BetterReads API")
                .version("v1")
                .description("Book tracking and review API.")
                .contact(new Contact().name("BetterReads"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://github.com/dahl-jar/betterreads/blob/main/LICENSE")))
            .servers(List.of(new Server().url(publicUrl).description("Production")))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access JWT from POST /api/v1/auth/login.")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
