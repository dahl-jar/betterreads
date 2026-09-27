package com.betterreads.clients.wikidata;

import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Reads book fields from a Wikidata entity's claims. */
final class WikidataClaims {

    private static final String SERIES_ORDINAL_QUALIFIER = "P1545";
    private static final String PUBLICATION_DATE_PROPERTY = "P577";
    private static final String PREFERRED_RANK = "preferred";
    private static final int YEAR_START = 1;
    private static final int YEAR_END = 5;

    private WikidataClaims() {
    }

    /** Returns the names of the QIDs the property references, dropping any the resolver cannot name. */
    static List<String> resolvedNames(
        final JsonNode entity,
        final String property,
        final Function<String, @Nullable String> resolveLabel
    ) {
        return WikidataTree.entityIds(entity, property).stream()
            .map(resolveLabel)
            .filter(name -> name != null)
            .toList();
    }

    /** Returns the publication year, preferring a {@code preferred}-rank claim when there are several. */
    static @Nullable Integer publicationYear(final JsonNode entity) {
        final List<JsonNode> claims = WikidataTree.claims(entity, PUBLICATION_DATE_PROPERTY).toList();
        return claims.stream()
            .filter(claim -> PREFERRED_RANK.equals(WikidataTree.text(claim.path("rank"))))
            .findFirst()
            .or(() -> claims.stream().findFirst())
            .map(WikidataClaims::year)
            .orElse(null);
    }

    static @Nullable Integer seriesPosition(final JsonNode entity) {
        return WikidataTree.claims(entity, WikidataTree.SERIES_PROPERTY)
            .findFirst()
            .map(claim -> parseInt(WikidataTree.firstQualifierValue(claim, SERIES_ORDINAL_QUALIFIER)))
            .orElse(null);
    }

    private static @Nullable Integer year(final JsonNode claim) {
        final String time = WikidataTree.text(WikidataTree.mainValue(claim).path("time"));
        if (time == null || time.length() < YEAR_END) {
            return null;
        }
        return parseInt(time.substring(YEAR_START, YEAR_END));
    }

    private static @Nullable Integer parseInt(final @Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
