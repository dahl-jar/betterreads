package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.function.Predicate;

import com.betterreads.booksource.SourceAuthor;
import org.jspecify.annotations.Nullable;

public final class HardcoverContributors {

    private static final String AUTHOR_ROLE = "Author";

    private static final String EDITOR_ROLE = "Editor";

    private HardcoverContributors() {
    }

    public static @Nullable List<SourceAuthor> authors(final List<HardcoverBookNode.Contribution> contributions) {
        final List<String> authors = namesWhere(contributions, HardcoverContributors::isAuthor);
        final List<String> names = authors.isEmpty()
            ? namesWhere(contributions, HardcoverContributors::isEditor)
            : authors;
        return names.isEmpty() ? null : SourceAuthor.ofNames(names);
    }

    private static List<String> namesWhere(
        final List<HardcoverBookNode.Contribution> contributions,
        final Predicate<@Nullable String> roleMatches
    ) {
        return contributions.stream()
            .filter(contribution -> roleMatches.test(contribution.contribution()))
            .map(HardcoverBookNode.Contribution::author)
            .filter(author -> author != null && author.name() != null)
            .map(HardcoverBookNode.Author::name)
            .toList();
    }

    private static boolean isAuthor(final @Nullable String role) {
        return role == null || AUTHOR_ROLE.equals(role);
    }

    private static boolean isEditor(final @Nullable String role) {
        return role != null && role.contains(EDITOR_ROLE);
    }
}
