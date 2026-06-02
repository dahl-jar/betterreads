package com.betterreads.integration.wikidata;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
 * Wikidata entity search and entity fetch.
 *
 * <p>4xx resolves to empty; 5xx and network failures propagate so the caller can retry.
 */
@Component
public class WikidataApi {

    private static final Logger LOG = LoggerFactory.getLogger(WikidataApi.class);

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SEARCH_PATH = "/w/api.php";
    private static final String ENTITY_PATH = "/wiki/Special:EntityData/";
    private static final String SEARCH_FIELD = "search";
    private static final int SEARCH_LIMIT = 8;

    private final WebClient wikidataWebClient;

    public WikidataApi(final WebClient wikidataWebClient) {
        this.wikidataWebClient = wikidataWebClient;
    }

    /** Returns candidate QIDs for the search term, or empty on a 4xx. */
    public List<String> searchCandidates(final String term) {
        final Optional<String> body = get(builder -> builder
            .path(SEARCH_PATH)
            .queryParam("action", "wbsearchentities")
            .queryParam(SEARCH_FIELD, term)
            .queryParam("language", "en")
            .queryParam("type", "item")
            .queryParam("format", "json")
            .queryParam("limit", SEARCH_LIMIT)
            .build());
        return body.map(WikidataApi::parseCandidates).orElseGet(List::of);
    }

    /** Returns the entity node under {@code entities.<qid>}, or empty on a 4xx or unknown id. */
    public Optional<JsonNode> entity(final String qid) {
        return get(builder -> builder.path(ENTITY_PATH + qid + ".json").build())
            .map(body -> JSON.readTree(body).path("entities").path(qid))
            .filter(node -> !node.isMissingNode() && !node.isEmpty());
    }

    private Optional<String> get(final Function<UriBuilder, URI> uri) {
        try {
            return Optional.ofNullable(wikidataWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .block());
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                LOG.debug("wikidata.get returned 4xx status={}", exception.getStatusCode().value());
                return Optional.empty();
            }
            throw exception;
        }
    }

    private static List<String> parseCandidates(final String body) {
        final List<String> ids = new ArrayList<>();
        for (final JsonNode hit : JSON.readTree(body).path(SEARCH_FIELD)) {
            final JsonNode hitId = hit.path("id");
            if (hitId.isValueNode()) {
                ids.add(hitId.asText());
            }
        }
        return ids;
    }
}
