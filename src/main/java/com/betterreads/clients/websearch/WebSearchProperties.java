package com.betterreads.clients.websearch;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "web-search")
public record WebSearchProperties(
    @NotBlank String bin,
    @NotBlank String model,
    @NotBlank String effort,
    @Positive int maxTurns,
    @NotNull Duration timeout,
    @Pattern(regexp = "[A-Za-z0-9_./-]+") String hookScript,
    @NotEmpty List<@Pattern(regexp = "[a-z0-9-]+(\\.[a-z0-9-]+)+") String> allowedDomains
) {

    public WebSearchProperties {
        allowedDomains = List.copyOf(allowedDomains);
    }
}
