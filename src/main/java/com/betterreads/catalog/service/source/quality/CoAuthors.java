package com.betterreads.catalog.service.source.quality;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.model.SourceAuthor;
import org.jspecify.annotations.Nullable;

public final class CoAuthors {

    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}]");

    private CoAuthors() {
    }

    public static @Nullable List<SourceAuthor> addFrom(
        final @Nullable List<SourceAuthor> chosen, final @Nullable List<SourceAuthor> candidates) {
        if (chosen == null || chosen.isEmpty() || candidates == null) {
            return chosen;
        }
        final Set<String> chosenKeys = chosen.stream().map(CoAuthors::key).collect(Collectors.toSet());
        final Set<String> candidateKeys = candidates.stream().map(CoAuthors::key).collect(Collectors.toSet());
        if (!candidateKeys.containsAll(chosenKeys)) {
            return chosen;
        }
        return Stream.concat(
                chosen.stream(),
                candidates.stream().filter(author -> !chosenKeys.contains(key(author))))
            .toList();
    }

    private static String key(final SourceAuthor author) {
        return NON_LETTERS.matcher(author.name()).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
