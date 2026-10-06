package com.betterreads.clients.websearch;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum SourceGroup {
    COMIC(List.of("comics", "graphic novel")),
    SFF(List.of("fantasy", "science fiction")),
    YOUNG_ADULT(List.of("young adult")),
    GENERAL(List.of());

    private final List<String> genres;

    SourceGroup(final List<String> genres) {
        this.genres = genres;
    }

    public static SourceGroup of(final Collection<String> genres) {
        final Set<String> stored = genres.stream()
            .map(genre -> genre.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        return Arrays.stream(values())
            .filter(group -> group.genres.stream().anyMatch(stored::contains))
            .findFirst()
            .orElse(GENERAL);
    }
}
