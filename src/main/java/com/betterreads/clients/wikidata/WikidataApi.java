package com.betterreads.clients.wikidata;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.clients.http.WebClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wikidata entity search and entity fetch.
 *
 * <p>A 4xx resolves to empty, 5xx and network failures propagate.
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

    public List<String> searchCandidates(final String term) {
        final Optional<String> body = responseBody(builder -> builder
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

    /** Returns the node at {@code entities.<qid>}, empty for an unknown id. */
    public Optional<JsonNode> entity(final String qid) {
        return responseBody(builder -> builder.path(ENTITY_PATH + qid + ".json").build())
            .map(body -> JSON.readTree(body).path("entities").path(qid))
            .filter(node -> !node.isMissingNode() && !node.isEmpty());
    }

    public Optional<String> enwikiTitle(final String qid) {
        return entity(qid).flatMap(entity -> Optional.ofNullable(WikidataLabels.enwikiTitle(entity)));
    }

    private Optional<String> responseBody(final Function<UriBuilder, URI> uri) {
        return WebClients.getBodyOrEmptyOn4xx(wikidataWebClient, uri, LOG, "wikidata.get");
    }

    private static List<String> parseCandidates(final String body) {
        return JSON.readTree(body).path(SEARCH_FIELD).valueStream()
            .map(hit -> hit.path("id"))
            .filter(JsonNode::isValueNode)
            .map(JsonNode::asString)
            .toList();
    }
}
