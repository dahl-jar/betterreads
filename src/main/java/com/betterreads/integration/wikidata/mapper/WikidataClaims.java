package com.betterreads.integration.wikidata.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Extracts book fields from a Wikidata entity, reading the claims tree via {@link WikidataTree}.
 */
public final class WikidataClaims {

    private static final String CLAIMS = "claims";
    private static final String SERIES_PROPERTY = "P179";
    private static final String SERIES_ORDINAL_QUALIFIER = "P1545";
    private static final String PUBLICATION_DATE_PROPERTY = "P577";
    private static final String PREFERRED_RANK = "preferred";
    private static final int YEAR_START = 1;
    private static final int YEAR_END = 5;

    private WikidataClaims() {
    }

    /** Returns the entity's display name via {@link WikidataLabels}, or null when the entity is empty. */
    public static @Nullable String label(final JsonNode entity) {
        if (entity.isMissingNode() || entity.isEmpty()) {
            return null;
        }
        final String qid = WikidataTree.text(entity.path("id"));
        return qid == null ? null : WikidataLabels.displayName(entity, qid);
    }

    /** Returns the referenced names of a property, each resolved by {@code resolveLabel}. */
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

    /** Returns the publication year, honoring a {@code preferred}-rank P577 claim over normal ones. */
    static @Nullable Integer publicationYear(final JsonNode entity) {
        final List<JsonNode> claims = new ArrayList<>();
        entity.path(CLAIMS).path(PUBLICATION_DATE_PROPERTY).forEach(claims::add);
        return claims.stream()
            .filter(claim -> PREFERRED_RANK.equals(WikidataTree.text(claim.path("rank"))))
            .findFirst()
            .or(() -> claims.stream().findFirst())
            .map(WikidataClaims::year)
            .orElse(null);
    }

    /** Returns the series ordinal from the P1545 qualifier on the first P179 claim, or null. */
    static @Nullable Integer seriesPosition(final JsonNode entity) {
        final JsonNode series = entity.path(CLAIMS).path(SERIES_PROPERTY);
        if (series.isEmpty()) {
            return null;
        }
        return parseInt(WikidataTree.firstQualifierValue(series.get(0), SERIES_ORDINAL_QUALIFIER));
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
