package com.betterreads.search.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Meilisearch connection settings bound from {@code meilisearch.*}.
 *
 * @param host base URL including scheme and port, e.g. http://meilisearch:7700
 * @param masterKey shared master key used for both admin and search calls
 * @param indexName name of the books index
 */
@Validated
@ConfigurationProperties(prefix = "meilisearch")
public record MeilisearchProperties(
    @NotBlank String host,
    @NotBlank String masterKey,
    @NotBlank String indexName
) {

    /**
     * Replaces the default record {@code toString} so the master key never
     * appears in logs, exception messages, or debug output.
     */
    @Override
    public String toString() {
        return "MeilisearchProperties[host=" + host + ", indexName=" + indexName + ", masterKey=***]";
    }
}
