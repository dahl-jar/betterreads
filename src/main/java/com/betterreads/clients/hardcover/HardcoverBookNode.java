package com.betterreads.clients.hardcover;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A {@code books} node from a Hardcover GraphQL query.
 *
 * <p>{@code canonicalId} differs from {@code id} on a translation or alternate edition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record HardcoverBookNode(
    @Nullable Long id,
    @Nullable String title,
    @Nullable String description,
    @Nullable Double rating,
    @Nullable Integer ratingsCount,
    @Nullable Integer usersCount,
    @Nullable Integer releaseYear,
    @Nullable Long canonicalId,
    @Nullable Integer bookCategoryId,
    @Nullable Boolean compilation,
    @Nullable Boolean isPartialBook,
    @Nullable Image image,
    @Nullable Edition defaultPhysicalEdition,
    @Nullable List<Contribution> contributions,
    @Nullable List<SeriesMembership> bookSeries
) {

    public static final String FIELDS = """
        id
        title
        description
        rating
        ratings_count
        users_count
        release_year
        canonical_id
        book_category_id
        compilation
        is_partial_book
        image { url }
        default_physical_edition { language { language } reading_format { format } }
        contributions { contribution author { name } }
        """;

    public HardcoverBookNode {
        contributions = NullableLists.copyOf(contributions);
        bookSeries = NullableLists.copyOf(bookSeries);
    }

    @Override
    @Nullable
    public List<Contribution> contributions() {
        return NullableLists.copyOf(contributions);
    }

    @Override
    @Nullable
    public List<SeriesMembership> bookSeries() {
        return NullableLists.copyOf(bookSeries);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(@Nullable String url) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public record Edition(@Nullable Language language, @Nullable ReadingFormat readingFormat) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(@Nullable String language) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReadingFormat(@Nullable String format) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contribution(@Nullable String contribution, @Nullable Author author) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeriesMembership(
        @Nullable Integer position, @Nullable Boolean featured, @Nullable Series series) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public record Series(@Nullable String name, @Nullable Integer primaryBooksCount) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(@Nullable String name) { }
}
