package com.betterreads.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SourceCollector}. A staged book carries only the discovery source's data;
 * the collector fetches the remaining sources for that book and merges them, so a candidate staged
 * from one search hit gains the rating, description, and other fields it needs to show.
 */
class SourceCollectorTest {

    private static final String ISBN = "9780441013593";

    private static final double HARDCOVER_RATING = 4.32;

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String HUGO = "Hugo Award";

    @Test
    @DisplayName("fetches every source by ISBN and merges them with the seed")
    void collectsAllSourcesByIsbn() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
        final SourceBook hardcoverHit = SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(TITLE)
            .averageRating(HARDCOVER_RATING)
            .build();
        final SourceCollector collector = new SourceCollector(
            new SourceMerger(), List.of(stubByIsbn(BookFieldSource.HARDCOVER, ISBN, hardcoverHit)));

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().averageRating())
            .as("Hardcover's rating, fetched by the seed's ISBN, must reach the merged book")
            .isEqualTo(HARDCOVER_RATING);
        assertThat(merged.book().title()).isEqualTo(TITLE);
    }

    @Test
    @DisplayName("falls back to title and author when the seed has no ISBN")
    void collectsByTitleAuthorWhenNoIsbn() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL1W")
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
        final SourceBook wikidataHit = SourceBook.builder(BookFieldSource.WIKIDATA)
            .title(TITLE)
            .awards(List.of(HUGO))
            .build();
        final SourceCollector collector = new SourceCollector(
            new SourceMerger(), List.of(stubByTitleAuthor(TITLE, AUTHOR, wikidataHit)));

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().awards())
            .as("with no ISBN, the collector fetches by title and author")
            .containsExactly(HUGO);
    }

    @Test
    @DisplayName("a source that returns nothing is skipped, the merge still proceeds")
    void skipsSourceWithNoMatch() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .build();
        final SourceCollector collector = new SourceCollector(
            new SourceMerger(), List.of(stubByIsbn(BookFieldSource.HARDCOVER, "other-isbn", null)));

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().title())
            .as("a non-matching source drops out; the seed still produces a merged book")
            .isEqualTo(TITLE);
    }

    private static BookSourceClient stubByIsbn(
        final BookFieldSource source, final String matchIsbn, final SourceBook hit) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                return matchIsbn.equals(isbn) ? Optional.ofNullable(hit) : Optional.empty();
            }
        };
    }

    private static BookSourceClient stubByTitleAuthor(
        final String matchTitle, final String matchAuthor, final SourceBook hit) {
        return new StubClient(BookFieldSource.WIKIDATA) {
            @Override
            public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
                return matchTitle.equals(title) && matchAuthor.equals(author)
                    ? Optional.ofNullable(hit) : Optional.empty();
            }
        };
    }

    private abstract static class StubClient implements BookSourceClient {

        private final BookFieldSource sourceId;

        StubClient(final BookFieldSource sourceId) {
            this.sourceId = sourceId;
        }

        @Override
        public BookFieldSource source() {
            return sourceId;
        }

        @Override
        public Optional<SourceBook> fetchByIsbn(final String isbn) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
            return Optional.empty();
        }
    }
}
