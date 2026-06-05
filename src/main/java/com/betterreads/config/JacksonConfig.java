package com.betterreads.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Jackson defaults.
 *
 * <p>Unknown JSON fields are ignored on read. Dates are written as ISO-8601 strings. Null
 * fields are dropped from output.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
            .findAndAddModules();
    }
}
