package com.betterreads.clients.loc;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Reads the series name and volume position from the MODS {@code relatedItem} series blocks.
 *
 * <p>A work can list its real series and a publisher imprint as sibling series blocks, and only the
 * real series numbers the volume, so the block with a {@code partNumber} wins.
 */
final class ModsSeries {

    private static final String PART_NUMBER_TAG = "partNumber";

    private static final Pattern PART_NUMBER = Pattern.compile("(\\d+)");

    private ModsSeries() {
    }

    static @Nullable String name(final JsonNode mods) {
        return preferredBlock(mods)
            .map(series -> LocSruTree.firstText(LocSruTree.firstByTag(series, "titleInfo"), "title"))
            .orElse(null);
    }

    /** first number in the part number, so {@code bk. 2} gives 2 */
    static Optional<Integer> position(final JsonNode mods) {
        return preferredBlock(mods)
            .map(series -> LocSruTree.firstText(series, PART_NUMBER_TAG))
            .map(PART_NUMBER::matcher)
            .filter(Matcher::find)
            .flatMap(matcher -> LocSruTree.intValue(matcher.group(1)));
    }

    private static Optional<JsonNode> preferredBlock(final JsonNode mods) {
        final List<JsonNode> series = LocSruTree.elements(mods, "relatedItem")
            .filter(LocSruTree.hasType("series"))
            .toList();
        return series.stream()
            .filter(item -> LocSruTree.firstByTag(item, PART_NUMBER_TAG) != null)
            .findFirst()
            .or(() -> series.stream().findFirst());
    }
}
