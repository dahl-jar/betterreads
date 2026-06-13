package com.betterreads.catalog.service.source;

import org.jspecify.annotations.Nullable;

/**
 * The identifiers a {@link DescriptionSource} keys on to find a book's description.
 *
 * <p>Wikipedia resolves through the Wikidata QID, Apple Books through the ISBN with a title and
 * author fallback, OpenLibrary through the work key, and Hardcover through its book id. Any field
 * may be null when the book lacks it.
 *
 * @param wikidataQid the Wikidata QID, e.g. {@code Q190192}
 * @param isbn13 the ISBN-13
 * @param title the book title
 * @param author the first author's name
 * @param openLibraryWorkKey the OpenLibrary work key, e.g. {@code OL893415W}
 * @param hardcoverId the Hardcover book id
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
