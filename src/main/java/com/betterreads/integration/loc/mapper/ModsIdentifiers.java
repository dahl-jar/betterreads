package com.betterreads.integration.loc.mapper;

import java.util.regex.Matcher;
import java.util.stream.Stream;

import com.betterreads.common.util.Isbn13;
import com.betterreads.integration.loc.SruTree;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class ModsIdentifiers {

    private static final String IDENTIFIER = "identifier";
    private static final String TYPE = "type";

    private ModsIdentifiers() {
    }

    static @Nullable String firstOfType(final JsonNode mods, final String type) {
        return ofType(mods, type).findFirst().orElse(null);
    }

    static @Nullable String isbn13(final JsonNode mods) {
        return ofType(mods, "isbn")
            .map(value -> Isbn13.PATTERN.matcher(value.replaceAll("\\D", "")))
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
