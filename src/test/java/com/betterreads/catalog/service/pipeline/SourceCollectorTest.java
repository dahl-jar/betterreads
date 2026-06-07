package com.betterreads.catalog.service.pipeline;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.BookSourceClient;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.catalog.service.source.SourceMerger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    private static final int YEAR = 1965;

    private static final int HTTP_SERVER_ERROR = 503;

    private static final String HARDCOVER_ID = "123";

    private static final String SERIES = "Dune Saga";

    private static final Executor SAME_THREAD = Runnable::run;

    private static final String OL_WORK_KEY = "OL1W";

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
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(stubByIsbn(BookFieldSource.HARDCOVER, ISBN, hardcoverHit)), SAME_THREAD);

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
            .openLibraryWorkKey(OL_WORK_KEY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
        final SourceBook wikidataHit = SourceBook.builder(BookFieldSource.WIKIDATA)
            .title(TITLE)
            .awards(List.of(HUGO))
            .build();
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(stubByTitleAuthor(TITLE, AUTHOR, wikidataHit)), SAME_THREAD);

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
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(stubByIsbn(BookFieldSource.HARDCOVER, "other-isbn", null)), SAME_THREAD);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().title())
            .as("a non-matching source drops out; the seed still produces a merged book")
            .isEqualTo(TITLE);
    }

    @Test
    @DisplayName("the seed's own source is not called again; its rating and series are kept")
    void skipsSeedsOwnSource() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(HARDCOVER_ID)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .averageRating(HARDCOVER_RATING)
            .seriesName(SERIES)
            .seriesPosition(YEAR)
            .build();
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(failingByTitleAuthor(BookFieldSource.HARDCOVER)), SAME_THREAD);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book())
            .as("the Hardcover seed is trusted as is; its rating and series survive the merge")
            .satisfies(book -> {
                assertThat(book.averageRating()).isEqualTo(HARDCOVER_RATING);
                assertThat(book.seriesName()).isEqualTo(SERIES);
            });
    }

    @Test
    @DisplayName("enriches a showable seed with later-wave fields it lacks")
    void enrichesShowableSeed() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .googleBooksVolumeId("gb1")
            .isbn13(ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .description("A long enough description to pass the show bar comfortably.")
            .coverUrl("https://example.com/c.jpg")
            .publicationYear(YEAR)
            .build();
        final SourceBook wikidataHit = SourceBook.builder(BookFieldSource.WIKIDATA)
            .title(TITLE)
            .awards(List.of(HUGO))
            .build();
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(stubByTitleAuthor(BookFieldSource.WIKIDATA, TITLE, AUTHOR, wikidataHit)), SAME_THREAD);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().awards())
            .as("a showable seed still runs the enrichment wave so Wikidata awards land")
            .containsExactly(HUGO);
    }

    @Test
    @DisplayName("runs both waves: a first-wave ISBN and a second-wave rating both reach the book")
    void runsBothWaves() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey(OL_WORK_KEY)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();
        final SourceBook googleHit = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .isbn13(ISBN)
            .title(TITLE)
            .build();
        final SourceBook hardcoverHit = SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(TITLE)
            .averageRating(HARDCOVER_RATING)
            .build();
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(
                stubByTitleAuthor(BookFieldSource.GOOGLE_BOOKS, TITLE, AUTHOR, googleHit),
                stubByTitleAuthor(BookFieldSource.HARDCOVER, TITLE, AUTHOR, hardcoverHit)), SAME_THREAD);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book())
            .as("the first-wave ISBN and the second-wave rating both merge into the book")
            .satisfies(book -> {
                assertThat(book.isbn13()).isEqualTo(ISBN);
                assertThat(book.averageRating()).isEqualTo(HARDCOVER_RATING);
            });
    }

    @Test
    @DisplayName("one source's 5xx is isolated; the other sources still merge into the book")
    void isolatesSourceFailure() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .build();
        final SourceBook hardcoverHit = SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .averageRating(HARDCOVER_RATING)
            .build();
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(
                failingByIsbn(BookFieldSource.GOOGLE_BOOKS),
                stubByIsbn(BookFieldSource.HARDCOVER, ISBN, hardcoverHit)), SAME_THREAD);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().averageRating())
            .as("a 503 from one source must not stop the others from enriching the book")
            .isEqualTo(HARDCOVER_RATING);
    }

    @Test
    @DisplayName("a source that throws a non-WebClient exception is dropped; the others still merge")
    void isolatesNonWebClientFailure() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .title(TITLE)
            .build();
        final SourceBook hardcoverHit = SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .averageRating(HARDCOVER_RATING)
            .build();
        final SourceCollector collector = new SourceCollector(new SourceMerger(),
            List.of(
                throwingByIsbn(BookFieldSource.GOOGLE_BOOKS),
                stubByIsbn(BookFieldSource.HARDCOVER, ISBN, hardcoverHit)), SAME_THREAD);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().averageRating())
            .as("a runtime exception from one source is contained; the rest still enrich the book")
            .isEqualTo(HARDCOVER_RATING);
    }

    private static BookSourceClient failingByIsbn(final BookFieldSource source) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                throw WebClientResponseException.create(
                    HTTP_SERVER_ERROR, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
            }
        };
    }

    private static BookSourceClient throwingByIsbn(final BookFieldSource source) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByIsbn(final String isbn) {
                throw new IllegalStateException("malformed response from " + source);
            }
        };
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
        return stubByTitleAuthor(BookFieldSource.WIKIDATA, matchTitle, matchAuthor, hit);
    }

    private static BookSourceClient stubByTitleAuthor(
        final BookFieldSource source, final String matchTitle, final String matchAuthor,
        final SourceBook hit) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
                return matchTitle.equals(title) && matchAuthor.equals(author)
                    ? Optional.ofNullable(hit) : Optional.empty();
            }
        };
    }

    private static BookSourceClient failingByTitleAuthor(final BookFieldSource source) {
        return new StubClient(source) {
            @Override
            public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
                throw new AssertionError("this source must not be called: " + source);
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
