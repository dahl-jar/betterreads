package com.betterreads.integration.wikidata.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Navigates a Wikidata entity's {@code claims} tree by property.
 *
 * <p>A claim's value lives at {@code claims.<prop>[i].mainsnak.datavalue.value}; only snaks of type
 * {@code value} carry data, so {@code novalue} and {@code somevalue} snaks are skipped.
 */
public final class WikidataTree {

    private static final String VALUE_SNAK = "value";
    private static final String CLAIMS = "claims";
    private static final String MAINSNAK = "mainsnak";
    private static final String SNAKTYPE = "snaktype";
    private static final String DATAVALUE = "datavalue";
    private static final String QUALIFIERS = "qualifiers";

    private WikidataTree() {
    }

    /** Returns the QIDs referenced by every {@code value} snak of the property, in document order. */
    public static List<String> entityIds(final JsonNode entity, final String property) {
        return mainValues(entity, property)
            .map(value -> text(value.path("id")))
            .filter(qid -> qid != null)
            .toList();
    }

    /** Returns the QID of the property's first {@code value} snak, or null when none. */
    static @Nullable String firstEntityId(final JsonNode entity, final String property) {
        final List<String> ids = entityIds(entity, property);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Returns the string value of the property's first {@code value} snak, or null when none. */
    static @Nullable String firstString(final JsonNode entity, final String property) {
        return mainValues(entity, property)
            .map(WikidataTree::text)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    /** Streams every claim of the property that carries a value. */
    static Stream<JsonNode> valueClaims(final JsonNode entity, final String property) {
        final List<JsonNode> claims = new ArrayList<>();
        for (final JsonNode claim : entity.path(CLAIMS).path(property)) {
            if (VALUE_SNAK.equals(text(claim.path(MAINSNAK).path(SNAKTYPE)))) {
                claims.add(claim);
            }
        }
        return claims.stream();
    }

    /** Returns the {@code datavalue.value} node of the claim's main snak. */
    static JsonNode mainValue(final JsonNode claim) {
        return claim.path(MAINSNAK).path(DATAVALUE).path(VALUE_SNAK);
    }

    /** Returns the value of the claim's first qualifier of the given property, or null. */
    static @Nullable String firstQualifierValue(final JsonNode claim, final String qualifier) {
        return text(claim.path(QUALIFIERS).path(qualifier).path(0).path(DATAVALUE).path(VALUE_SNAK));
    }

    /** Returns the node's text when it is a value node, otherwise null. */
    static @Nullable String text(final JsonNode node) {
        return node.isValueNode() ? node.asText() : null;
    }

    private static Stream<JsonNode> mainValues(final JsonNode entity, final String property) {
        return valueClaims(entity, property).map(WikidataTree::mainValue);
    }
}
