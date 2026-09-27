package com.betterreads.booksource;

import org.jspecify.annotations.Nullable;

/**
 * Identifiers a description source can look a book up by. Any of them is null when the book lacks it.
 *
 * @param wikidataQid e.g. {@code Q190192}
 * @param author the first author's name
 * @param openLibraryWorkKey e.g. {@code OL893415W}
 */
public record DescriptionLookup(
    @Nullable String wikidataQid,
    @Nullable String isbn13,
    @Nullable String title,
    @Nullable String author,
    @Nullable String openLibraryWorkKey,
    @Nullable String hardcoverId
) {
}
