package com.betterreads.bookmerge;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.betterreads.booksource.SourceAuthor;
import com.betterreads.text.AuthorNames;
import org.jspecify.annotations.Nullable;

final class CoAuthors {

    private CoAuthors() {
    }

    static @Nullable List<SourceAuthor> addFrom(
        final @Nullable List<SourceAuthor> chosen, final @Nullable List<SourceAuthor> candidates) {
        if (chosen == null || chosen.isEmpty() || candidates == null) {
            return chosen;
        }
        final Set<String> chosenKeys = keys(chosen);
        if (!keys(candidates).containsAll(keys(SourceAuthor.primary(chosen)))) {
            return chosen;
        }
        return Stream.concat(
                chosen.stream(),
                candidates.stream().filter(author -> !chosenKeys.contains(key(author))))
            .toList();
    }

    private static Set<String> keys(final List<SourceAuthor> authors) {
        return authors.stream().map(CoAuthors::key).collect(Collectors.toSet());
    }

    private static String key(final SourceAuthor author) {
        return AuthorNames.key(author.name());
    }
}
