package com.betterreads.integration.loc.mapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.betterreads.integration.loc.SruTree;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Reads identifiers from the MODS {@code identifier} elements.
 *
 * <p>A record carries several identifiers of different types, and ISBNs arrive as separate ISBN-13
 * and ISBN-10 elements with occasional volume-suffix junk, so the ISBN-13 is found by digit match
 * rather than by position.
 */
final class ModsIdentifiers {

    private static final String IDENTIFIER = "identifier";
    private static final String TYPE = "type";

    private static final Pattern ISBN_13 = Pattern.compile("97[89]\\d{10}");

    private ModsIdentifiers() {
    }

    /** Returns the first identifier of the given type, or null. */
    static @Nullable String firstOfType(final JsonNode mods, final String type) {
        return ofType(mods, type).findFirst().orElse(null);
    }

    /** Returns the first ISBN-13 across the isbn identifiers, ignoring suffixes, or null. */
    static @Nullable String isbn13(final JsonNode mods) {
        return ofType(mods, "isbn")
            .map(value -> ISBN_13.matcher(value.replaceAll("\\D", "")))
            .filter(Matcher::find)
            .map(Matcher::group)
            .findFirst()
            .orElse(null);
    }

    private static Stream<String> ofType(final JsonNode mods, final String type) {
        return SruTree.elements(mods, IDENTIFIER)
            .filter(node -> type.equals(SruTree.attribute(node, TYPE)))
            .map(SruTree::text)
            .filter(value -> value != null);
    }
}
