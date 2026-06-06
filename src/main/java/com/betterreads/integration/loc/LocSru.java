package com.betterreads.integration.loc;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Runs one SRU {@code searchRetrieve} call and returns the raw record XML.
 *
 * <p>Shared by the enrichment and discovery clients: both issue the same query shape and need the
 * same 4xx-to-empty / 5xx-propagates contract, differing only in the CQL and record schema. 4xx
 * resolves to empty; 5xx and network failures propagate.
 */
@Component
public class LocSru {

    private static final Logger LOG = LoggerFactory.getLogger(LocSru.class);

    private static final String VERSION = "1.1";
    private static final String OPERATION = "searchRetrieve";

    private final WebClient locWebClient;

    public LocSru(final WebClient locWebClient) {
        this.locWebClient = locWebClient;
    }

    /**
     * Returns the response body for a {@code searchRetrieve} starting at {@code startRecord}, or
     * empty on a 4xx.
     *
     * @param cql the CQL query
     * @param recordSchema the record schema, e.g. {@code mods} or {@code marcxml}
     * @param startRecord the 1-based index of the first record to return
     * @param maximumRecords the page size
     */
    public Optional<String> searchRetrieve(
        final String cql,
        final String recordSchema,
        final int startRecord,
        final int maximumRecords
    ) {
        try {
            final String body = locWebClient.get()
                .uri(builder -> builder
                    .queryParam("version", VERSION)
                    .queryParam("operation", OPERATION)
                    .queryParam("query", cql)
                    .queryParam("startRecord", startRecord)
                    .queryParam("maximumRecords", maximumRecords)
                    .queryParam("recordSchema", recordSchema)
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
            return Optional.ofNullable(body);
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                LOG.debug("loc.searchRetrieve returned 4xx status={}", exception.getStatusCode().value());
                return Optional.empty();
            }
            throw exception;
        }
    }
}
