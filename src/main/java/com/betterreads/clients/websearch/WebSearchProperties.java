package com.betterreads.clients.websearch;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    @NotEmpty Map<SourceGroup, List<@Pattern(regexp = DOMAIN) String>> groupDomains,
    @NotEmpty List<@Pattern(regexp = DOMAIN) String> sharedDomains,
    @NotNull List<String> searchOnlyDomains,
    @Positive int pageConnectTimeout,
    @Positive int pageReadTimeout,
    @Positive int pageMaxBytes,
    @NotEmpty List<String> pageSchemes
) {

    private static final String DOMAIN = "[a-z0-9-]+(\\.[a-z0-9-]+)+";

    public WebSearchProperties {
        final Map<SourceGroup, List<String>> groups = new EnumMap<>(SourceGroup.class);
        groupDomains.forEach((group, domains) -> groups.put(group, List.copyOf(domains)));
        groupDomains = Collections.unmodifiableMap(groups);
        sharedDomains = List.copyOf(sharedDomains);
        searchOnlyDomains = List.copyOf(searchOnlyDomains);
        pageSchemes = List.copyOf(pageSchemes);
    }

    public List<String> allowedDomains() {
        return Stream.concat(groupDomains.values().stream().flatMap(List::stream), sharedDomains.stream())
            .distinct()
            .toList();
    }

    public List<String> domains(final SourceGroup group) {
        return Stream.concat(groupDomains.getOrDefault(group, List.of()).stream(), sharedDomains.stream())
            .distinct()
            .toList();
    }
}
