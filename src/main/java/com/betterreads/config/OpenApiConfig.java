package com.betterreads.config;

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
 * Springdoc/OpenAPI metadata for {@code /v3/api-docs} and Swagger UI. Declares a bearer-JWT
 * security scheme so the UI surfaces an Authorize button; endpoints opt out by setting
 * {@code @Operation(security = {})}. The {@code servers} list is sourced from
 * {@code springdoc.public-url} so the deployed origin replaces the local default at runtime.
 */
@Configuration
public class OpenApiConfig {

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
                .description("Book tracking and recommendation API.")
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
