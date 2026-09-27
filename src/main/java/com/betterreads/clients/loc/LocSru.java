package com.betterreads.clients.loc;

import java.util.Optional;

import com.betterreads.clients.http.WebClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Raw SRU {@code searchRetrieve} calls. A 4xx comes back empty, 5xx and network errors are thrown. */
@Component
class LocSru {

    private static final Logger LOG = LoggerFactory.getLogger(LocSru.class);

    private static final String VERSION = "1.1";
    private static final String OPERATION = "searchRetrieve";
    private static final String MODS_SCHEMA = "mods";
    private static final int FIRST_RECORD = 1;
    private static final int MAX_RECORDS = 1;

    private final WebClient locWebClient;

    LocSru(final WebClient locWebClient) {
        this.locWebClient = locWebClient;
    }

    Optional<String> searchRetrieve(final String cql) {
        return WebClients.getBodyOrEmptyOn4xx(locWebClient, builder -> builder
            .queryParam("version", VERSION)
            .queryParam("operation", OPERATION)
            .queryParam("query", cql)
            .queryParam("startRecord", FIRST_RECORD)
            .queryParam("maximumRecords", MAX_RECORDS)
            .queryParam("recordSchema", MODS_SCHEMA)
            .build(), LOG, "loc.searchRetrieve");
    }
}
