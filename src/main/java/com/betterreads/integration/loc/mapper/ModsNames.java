package com.betterreads.integration.loc.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.betterreads.integration.loc.SruTree;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class ModsNames {

    private static final String TYPE = "type";

    private static final String PRIMARY = "primary";

    private static final Set<String> AUTHOR_ROLES =
        Set.of("author", "editor", "screenwriter", "writer", "compiler");

    private static final Pattern PARENTHETICAL = Pattern.compile("\\s*\\([^)]*\\)");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[,\\s]+$|(?<=\\p{L}{2})\\.\\s*$");

    private ModsNames() {
    }

    static List<String> authorNames(final JsonNode mods) {
        return SruTree.elements(mods, "name")
            .filter(ModsNames::isCreditedAuthor)
            .sorted(Comparator.comparing(name -> !isPrimary(name)))
            .map(ModsNames::catalogNamePart)
            .filter(namePart -> namePart != null)
            .map(ModsNames::displayName)
            .toList();
    }

    private static String displayName(final String catalogName) {
        final String bare = TRAILING_PUNCTUATION.matcher(PARENTHETICAL.matcher(catalogName).replaceAll("").strip())
            .replaceAll("");
        final int comma = bare.indexOf(',');
        if (comma < 0) {
            return bare;
        }
        return bare.substring(comma + 1).strip() + " " + bare.substring(0, comma).strip();
    }

    private static boolean isCreditedAuthor(final JsonNode name) {
        final List<String> roles = SruTree.elements(name, "role")
            .flatMap(role -> SruTree.elements(role, "roleTerm"))
            .map(SruTree::text)
            .filter(role -> role != null)
            .map(role -> role.strip().toLowerCase(Locale.ROOT))
            .toList();
        return roles.isEmpty() ? isPrimary(name) : roles.stream().anyMatch(AUTHOR_ROLES::contains);
    }

    private static boolean isPrimary(final JsonNode name) {
        return PRIMARY.equals(SruTree.attribute(name, "usage"));
    }

    private static @Nullable String catalogNamePart(final JsonNode name) {
        return SruTree.elements(name, "namePart")
            .filter(part -> SruTree.attribute(part, TYPE) == null)
            .map(SruTree::text)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }
}
