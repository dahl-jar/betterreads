package com.betterreads.clients.loc;

import java.util.regex.Matcher;
import java.util.stream.Stream;

import com.betterreads.isbn.Isbn13;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class ModsIdentifiers {

    private static final String IDENTIFIER = "identifier";

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
        return LocSruTree.elements(mods, IDENTIFIER)
            .filter(LocSruTree.hasType(type))
            .flatMap(LocSruTree::textOf);
    }
}
