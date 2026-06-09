package com.betterreads.integration.wikipedia;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wikipedia REST summary fetch.
 *
 * <p>Returns the page extract only for a {@code standard} page; a disambiguation page or any other
 * type yields empty, as does a 4xx. 5xx and network failures propagate.
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

    /** Returns the extract for a standard page with the given title, or empty otherwise. */
    public Optional<String> summaryExtract(final String pageTitle) {
        return get(builder -> builder.path(SUMMARY_PATH).pathSegment(pageTitle).build())
            .map(JSON::readTree)
            .filter(WikipediaApi::isStandard)
            .map(node -> node.path("extract").asString(""))
            .filter(extract -> !extract.isBlank());
    }

    private static boolean isStandard(final JsonNode summary) {
        return STANDARD_TYPE.equals(summary.path("type").asString(""));
    }

    private Optional<String> get(final Function<UriBuilder, URI> uri) {
        try {
            return Optional.ofNullable(wikipediaWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .block());
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                LOG.debug("wikipedia.get returned 4xx status={}", exception.getStatusCode().value());
                return Optional.empty();
            }
            throw exception;
        }
    }
}
