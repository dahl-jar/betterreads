package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.CreditRole;
import com.betterreads.booksource.SourceAuthor;
import org.jspecify.annotations.Nullable;

public final class HardcoverContributors {

    private HardcoverContributors() {
    }

    public static @Nullable List<SourceAuthor> credits(final List<HardcoverBookNode.Contribution> contributions) {
        final List<SourceAuthor> credits = contributions.stream()
            .flatMap(contribution -> credit(contribution).stream())
            .toList();
        return credits.isEmpty() ? null : credits;
    }

    private static Optional<SourceAuthor> credit(final HardcoverBookNode.Contribution contribution) {
        final HardcoverBookNode.Author author = contribution.author();
        final String name = author == null ? null : author.name();
        return name == null
            ? Optional.empty()
            : Optional.of(SourceAuthor.withRole(name, CreditRole.fromSourceText(contribution.contribution())));
    }
}
