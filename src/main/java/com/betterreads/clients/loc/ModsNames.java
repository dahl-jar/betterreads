package com.betterreads.clients.loc;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.betterreads.booksource.CreditRole;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.text.AuthorNames;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class ModsNames {

    private static final String TYPE = "type";

    private static final String PRIMARY = "primary";

    private static final Pattern PARENTHETICAL = Pattern.compile("\\s*\\([^)]*\\)");

    private ModsNames() {
    }

    static List<SourceAuthor> credits(final JsonNode mods) {
        return LocSruTree.elements(mods, "name")
            .sorted(Comparator.comparing(name -> !isPrimary(name)))
            .flatMap(name -> credit(name).stream())
            .toList();
    }

    private static Optional<SourceAuthor> credit(final JsonNode name) {
        final String namePart = catalogNamePart(name);
        return namePart == null
            ? Optional.empty()
            : Optional.of(SourceAuthor.withRole(
                AuthorNames.display(PARENTHETICAL.matcher(namePart).replaceAll("")), role(name)));
    }

    private static CreditRole role(final JsonNode name) {
        final CreditRole roleless = isPrimary(name) ? CreditRole.AUTHOR : CreditRole.OTHER;
        return LocSruTree.elements(name, "role")
            .flatMap(role -> LocSruTree.elements(role, "roleTerm"))
            .flatMap(LocSruTree::textOf)
            .map(CreditRole::fromSourceText)
            .min(Comparator.naturalOrder())
            .orElse(roleless);
    }

    private static boolean isPrimary(final JsonNode name) {
        return PRIMARY.equals(LocSruTree.attribute(name, "usage"));
    }

    private static @Nullable String catalogNamePart(final JsonNode name) {
        return LocSruTree.elements(name, "namePart")
            .filter(part -> LocSruTree.attribute(part, TYPE) == null)
            .flatMap(LocSruTree::textOf)
            .findFirst()
            .orElse(null);
    }
}
