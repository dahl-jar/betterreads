package com.betterreads.integration.loc.mapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.integration.loc.SruTree;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Reads the series name and volume position from the MODS {@code relatedItem} series blocks.
 *
 * <p>A work can list both its real series and a publisher imprint as sibling series blocks, and only
 * the real series numbers the volume, so the block carrying a {@code partNumber} is preferred.
 */
final class ModsSeries {

    private static final String TYPE = "type";
    private static final String PART_NUMBER_TAG = "partNumber";

    private static final Pattern PART_NUMBER = Pattern.compile("(\\d+)");

    private ModsSeries() {
    }

    /** Returns the series title, or null when no series block is present. */
    static @Nullable String name(final JsonNode mods) {
        return preferredBlock(mods)
            .map(series -> SruTree.firstText(SruTree.firstByTag(series, "titleInfo"), "title"))
            .orElse(null);
    }

    /** Returns the volume position parsed from the part number, or empty. */
    static Optional<Integer> position(final JsonNode mods) {
        return preferredBlock(mods)
            .map(series -> SruTree.firstText(series, PART_NUMBER_TAG))
            .map(PART_NUMBER::matcher)
            .filter(Matcher::find)
            .flatMap(matcher -> SruTree.intValue(matcher.group(1)));
    }

    private static Optional<JsonNode> preferredBlock(final JsonNode mods) {
        final List<JsonNode> series = SruTree.elements(mods, "relatedItem")
            .filter(related -> "series".equals(SruTree.attribute(related, TYPE)))
            .toList();
        return series.stream()
            .filter(item -> SruTree.firstByTag(item, PART_NUMBER_TAG) != null)
            .findFirst()
            .or(() -> series.stream().findFirst());
    }
}
