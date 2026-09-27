package com.betterreads.clients.wikipedia;

import java.util.Optional;

import com.betterreads.clients.http.WebClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wikipedia REST summary fetch.
 *
 * <p>Only a {@code standard} page yields an extract, so a disambiguation page comes back empty. A 4xx
 * is empty too, 5xx and network failures propagate.
 */
@Component
public class WikipediaApi {

    private static final Logger LOG = LoggerFactory.getLogger(WikipediaApi.class);

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SUMMARY_PATH = "/api/rest_v1/page/summary";

    private static final String STANDARD_TYPE = "standard";

    private final WebClient wikipediaWebClient;

    public WikipediaApi(final WebClient wikipediaWebClient) {
        this.wikipediaWebClient = wikipediaWebClient;
    }

    public Optional<String> summaryExtract(final String pageTitle) {
        return WebClients.getBodyOrEmptyOn4xx(wikipediaWebClient,
                builder -> builder.path(SUMMARY_PATH).pathSegment(pageTitle).build(), LOG, "wikipedia.get")
            .map(JSON::readTree)
            .filter(WikipediaApi::isStandard)
            .map(node -> node.path("extract").asString(""))
            .filter(extract -> !extract.isBlank());
    }

    private static boolean isStandard(final JsonNode summary) {
        return STANDARD_TYPE.equals(summary.path("type").asString(""));
    }
}
