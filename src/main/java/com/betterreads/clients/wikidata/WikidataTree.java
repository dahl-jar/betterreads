package com.betterreads.clients.wikidata;

import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Navigates a Wikidata entity's {@code claims} tree by property.
 *
 * <p>A claim's value lives at {@code claims.<prop>[i].mainsnak.datavalue.value}. Only snaks of type
 * {@code value} carry data, so {@code novalue} and {@code somevalue} snaks are skipped.
 */
final class WikidataTree {

    static final String AUTHOR_PROPERTY = "P50";
    static final String SERIES_PROPERTY = "P179";
    private static final String CLAIMS_KEY = "claims";
    private static final String VALUE = "value";
    private static final String VALUE_SNAK = VALUE;
    private static final String MAINSNAK = "mainsnak";
    private static final String SNAKTYPE = "snaktype";
    private static final String DATAVALUE = "datavalue";
    private static final String QUALIFIERS = "qualifiers";

    private WikidataTree() {
    }

    /** Returns the QIDs the property references, in document order. */
    static List<String> entityIds(final JsonNode entity, final String property) {
        return mainValues(entity, property)
            .map(value -> text(value.path("id")))
            .filter(qid -> qid != null)
            .toList();
    }

    static @Nullable String firstString(final JsonNode entity, final String property) {
        return mainValues(entity, property)
            .map(WikidataTree::text)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    static JsonNode mainValue(final JsonNode claim) {
        return claim.path(MAINSNAK).path(DATAVALUE).path(VALUE);
    }

    static @Nullable String firstQualifierValue(final JsonNode claim, final String qualifier) {
        return text(claim.path(QUALIFIERS).path(qualifier).path(0).path(DATAVALUE).path(VALUE));
    }

    static @Nullable String text(final JsonNode node) {
        return node.isValueNode() ? node.asString() : null;
    }

    static Stream<JsonNode> claims(final JsonNode entity, final String property) {
        return entity.path(CLAIMS_KEY).path(property).valueStream();
    }

    private static Stream<JsonNode> mainValues(final JsonNode entity, final String property) {
        return claims(entity, property)
            .filter(claim -> VALUE_SNAK.equals(text(claim.path(MAINSNAK).path(SNAKTYPE))))
            .map(WikidataTree::mainValue);
    }
}
