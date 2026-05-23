package com.betterreads.search.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Document shape stored in the Meilisearch books index.
 *
 * @param bookId stable primary key matching the local catalog id
 * @param title book title
 * @param subtitle book subtitle if any
 * @param authors author display names
 * @param subjects BISAC subjects assigned by the catalog mapper
 * @param language ISO 639-1 code
 * @param publicationYear year of original publication
 * @param popularityScore tie-breaker for sort, 0 if unknown
 */
public record BookSearchDocument(
    String bookId,
    String title,
    @Nullable String subtitle,
    List<String> authors,
    List<String> subjects,
    @Nullable String language,
    @Nullable Integer publicationYear,
    double popularityScore
) {

    public BookSearchDocument {
        authors = List.copyOf(authors);
        subjects = List.copyOf(subjects);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }

    @Override
    public List<String> subjects() {
        return List.copyOf(subjects);
    }
}
