package com.betterreads.booksource;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Author as a source returns it. {@code wikidataQid}, {@code photoUrl} and {@code bio} are null
 * unless the source resolves author entities.
 */
public record SourceAuthor(
        String name,
        @Nullable String wikidataQid,
        @Nullable String photoUrl,
        @Nullable String bio) {

    public static SourceAuthor ofName(final String name) {
        return new SourceAuthor(name, null, null, null);
    }

    public static @Nullable List<SourceAuthor> ofNames(final @Nullable List<String> names) {
        return names == null ? null : names.stream().map(SourceAuthor::ofName).toList();
    }
}
