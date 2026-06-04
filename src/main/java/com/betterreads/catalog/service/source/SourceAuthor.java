package com.betterreads.catalog.service.source;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One author returned by a {@link BookSourceClient}.
 *
 * <p>{@code wikidataQid}, {@code photoUrl}, and {@code bio} are null unless the source resolves
 * author entities.
 */
public record SourceAuthor(
        String name,
        @Nullable String wikidataQid,
        @Nullable String photoUrl,
        @Nullable String bio) {

    /** Returns an author with only a name. */
    public static SourceAuthor ofName(final String name) {
        return new SourceAuthor(name, null, null, null);
    }

    /** Wraps each name as a name-only author, or returns null when {@code names} is null. */
    public static @Nullable List<SourceAuthor> ofNames(final @Nullable List<String> names) {
        return names == null ? null : names.stream().map(SourceAuthor::ofName).toList();
    }
}
