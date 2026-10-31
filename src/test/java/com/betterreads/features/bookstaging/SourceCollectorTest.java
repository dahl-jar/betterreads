package com.betterreads.features.bookstaging;

import com.betterreads.bookdescription.DescriptionSelector;
import com.betterreads.bookmerge.SourceMerger;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.BookSourceClient;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;

import static com.betterreads.features.bookstaging.DuneBooks.AUTHOR;
import static com.betterreads.features.bookstaging.DuneBooks.ISBN;
import static com.betterreads.features.bookstaging.DuneBooks.SEQUEL_TITLE;
import static com.betterreads.features.bookstaging.DuneBooks.TITLE;
import static com.betterreads.features.bookstaging.StubSourceClients.emptyFor;
import static com.betterreads.features.bookstaging.StubSourceClients.malformedByIsbn;
import static com.betterreads.features.bookstaging.StubSourceClients.recordingIsbnCalls;
import static com.betterreads.features.bookstaging.StubSourceClients.stubByIsbn;
import static com.betterreads.features.bookstaging.StubSourceClients.stubByTitleAuthor;
import static com.betterreads.features.bookstaging.StubSourceClients.unavailableByIsbn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

// PMD.TooManyMethods: one test per collect route plus the shared collector builders.
@SuppressWarnings("PMD.TooManyMethods")
@ExtendWith(OutputCaptureExtension.class)
class SourceCollectorTest {

    private static final String DROPPED_HIT = "catalog.collect dropped ISBN hit with another title";

    private static final double HARDCOVER_RATING = 4.32;

    private static final double WRONG_RATING = 3.1;

    private static final String DELUXE_TITLE = "Dune: Deluxe Edition";

    private static final String HUGO = "Hugo Award";

    private static final Executor SAME_THREAD = Runnable::run;

    private static final long SLOW_SOURCE_MILLIS = 200;

    private static final Duration SHORT_WAVE_TIMEOUT = Duration.ofMillis(50);

    private static final String OL_WORK_KEY = "OL1W";

    private static final SourceBook ISBN_SEED = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
        .isbn13(ISBN)
        .title(TITLE)
        .build();

    private static final SourceBook WORK_KEY_SEED = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
        .openLibraryWorkKey(OL_WORK_KEY)
        .title(TITLE)
        .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
        .build();

    private static final SourceBook HARDCOVER_RATING_HIT = hardcoverHit(TITLE, HARDCOVER_RATING);

    @Test
    @DisplayName("fetches every source by ISBN and merges them with the seed")
    void collectsAllSourcesByIsbn() {
        final SourceCollector collector =
            collectorWith(SAME_THREAD, stubByIsbn(BookFieldSource.HARDCOVER, ISBN, HARDCOVER_RATING_HIT));

        final MergedBook merged = collector.collectFor(DuneBooks.openLibraryDune());

        assertThat(merged.book().averageRating())
            .as("Hardcover's rating, fetched by the seed's ISBN, must reach the merged book")
            .isEqualTo(HARDCOVER_RATING);
    }

    @Test
    @DisplayName("falls back to title and author when the seed has no ISBN")
    void collectsByTitleAuthorWhenNoIsbn() {
        final List<String> awards = awardsFromWikidataTitleAuthorHit(WORK_KEY_SEED);

        assertThat(awards)
            .as("with no ISBN, the collector fetches by title and author")
            .containsExactly(HUGO);
    }

    @Test
    @DisplayName("falls back to title and author when the ISBN lookup misses")
    void shouldFetchByTitleAndAuthorWhenIsbnLookupMisses() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .googleBooksVolumeId("gb1")
            .isbn13(ISBN)
            .title(TITLE)
            .authors(SourceAuthor.ofNames(List.of(AUTHOR)))
            .build();

        final List<String> awards = awardsFromWikidataTitleAuthorHit(seed);

        assertThat(awards)
            .as("Wikidata has no ISBN match, so its title and author match supplies the awards")
            .containsExactly(HUGO);
    }

    @Test
    @DisplayName("runs both waves: a first-wave ISBN and a second-wave rating both reach the book")
    void runsBothWaves() {
        final SourceBook googleHit = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .isbn13(ISBN)
            .title(TITLE)
            .build();
        final SourceBook hardcoverHit = SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(TITLE)
            .averageRating(HARDCOVER_RATING)
            .build();
        final SourceCollector collector = collectorWith(SAME_THREAD,
            stubByTitleAuthor(BookFieldSource.GOOGLE_BOOKS, TITLE, AUTHOR, googleHit),
            stubByTitleAuthor(BookFieldSource.HARDCOVER, TITLE, AUTHOR, hardcoverHit));

        final MergedBook merged = collector.collectFor(WORK_KEY_SEED);

        assertThat(merged.book())
            .as("the first-wave ISBN and the second-wave rating both merge into the book")
            .satisfies(book -> {
                assertThat(book.isbn13()).isEqualTo(ISBN);
                assertThat(book.averageRating()).isEqualTo(HARDCOVER_RATING);
            });
    }

    static Stream<Arguments> failingGoogleBooks() {
        return Stream.of(
            arguments("503", unavailableByIsbn(BookFieldSource.GOOGLE_BOOKS)),
            arguments("malformed response", malformedByIsbn(BookFieldSource.GOOGLE_BOOKS)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingGoogleBooks")
    @DisplayName("a failing source is left unresolved, and the other sources still merge into the book")
    void isolatesSourceFailure(final String failure, final BookSourceClient failing) {
        final SourceCollector collector = collectorWith(SAME_THREAD,
            failing,
            stubByIsbn(BookFieldSource.HARDCOVER, ISBN, HARDCOVER_RATING_HIT));

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.book().averageRating())
            .as("a %s from one source must not stop the others from filling the book", failure)
            .isEqualTo(HARDCOVER_RATING);
        assertThat(merged.resolvedSources())
            .as("Google failed, so it did not resolve. Hardcover and the seed did")
            .contains(BookFieldSource.HARDCOVER, BookFieldSource.OPEN_LIBRARY)
            .doesNotContain(BookFieldSource.GOOGLE_BOOKS);
    }

    @Test
    @DisplayName("a source still running at the wave timeout is left unresolved, and the others still merge")
    void shouldDropSourceStillRunningAtWaveTimeout() {
        final AtomicBoolean firstTask = new AtomicBoolean(true);
        final Executor dropsFirstTask = task -> {
            if (!firstTask.getAndSet(false)) {
                task.run();
            }
        };
        final SourceCollector collector = new SourceCollector(
            new SourceMerger(),
            List.of(
                stubByIsbn(BookFieldSource.GOOGLE_BOOKS, ISBN, null),
                stubByIsbn(BookFieldSource.HARDCOVER, ISBN, HARDCOVER_RATING_HIT)),
            new DescriptionSelector(List.of()), dropsFirstTask, SHORT_WAVE_TIMEOUT);

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
        assertThat(merged.resolvedSources()).doesNotContain(BookFieldSource.GOOGLE_BOOKS);
    }

    @Test
    @DisplayName("each other source is fetched once per collect, and the seed's own source never")
    void shouldFetchEachOtherSourceOnce() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(TITLE)
            .build();
        final List<BookFieldSource> calls = new ArrayList<>();
        final SourceCollector collector = collectorWith(SAME_THREAD,
            recordingIsbnCalls(BookFieldSource.GOOGLE_BOOKS, calls),
            recordingIsbnCalls(BookFieldSource.HARDCOVER, calls),
            recordingIsbnCalls(BookFieldSource.WIKIDATA, calls));

        collector.collectFor(seed);

        assertThat(calls).containsExactlyInAnyOrder(BookFieldSource.GOOGLE_BOOKS, BookFieldSource.WIKIDATA);
    }

    @Test
    @DisplayName("a source answering on another thread is waited for before the merge")
    void shouldWaitForSlowSourceBeforeMerging() {
        final Executor delayed = CompletableFuture.delayedExecutor(SLOW_SOURCE_MILLIS, TimeUnit.MILLISECONDS);
        final SourceCollector collector =
            collectorWith(delayed, stubByIsbn(BookFieldSource.HARDCOVER, ISBN, HARDCOVER_RATING_HIT));

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
    }

    @Test
    @DisplayName("an interrupt while waiting for sources stays set on the calling thread")
    // PMD.DoNotUseThreads: the test sets and reads the interrupt flag of the calling thread
    @SuppressWarnings("PMD.DoNotUseThreads")
    void shouldKeepInterruptFlagWhenInterruptedWhileWaiting() {
        final Executor delayed = CompletableFuture.delayedExecutor(SLOW_SOURCE_MILLIS, TimeUnit.MILLISECONDS);
        final SourceCollector collector = collectorWith(delayed, stubByIsbn(BookFieldSource.HARDCOVER, ISBN, null));
        Thread.currentThread().interrupt();

        collector.collectFor(ISBN_SEED);

        final boolean interrupted = Thread.interrupted();
        assertThat(interrupted).isTrue();
    }

    @Test
    void shouldNotCountACleanHardcoverMissAsResolved() {
        final SourceCollector collector = collectorWith(SAME_THREAD,
            emptyFor(BookFieldSource.HARDCOVER));

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.resolvedSources()).doesNotContain(BookFieldSource.HARDCOVER);
    }

    @Test
    void shouldUseTitleLookupWhenIsbnHitHasAnotherTitle() {
        final BookSourceClient titleLookup =
            stubByTitleAuthor(BookFieldSource.HARDCOVER, TITLE, AUTHOR, HARDCOVER_RATING_HIT);
        final SourceCollector collector = collectorWith(SAME_THREAD,
            StubSourceClients.withIsbnHit(titleLookup, ISBN, hardcoverHit(SEQUEL_TITLE, WRONG_RATING)));

        final MergedBook merged = collector.collectFor(DuneBooks.openLibraryDune());

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
    }

    @Test
    void shouldLeaveSourceUnresolvedWhenDroppedHitHasNoFallback() {
        final SourceCollector collector = collectorWithIsbnHit(SEQUEL_TITLE, WRONG_RATING);

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.book().averageRating()).isNull();
        assertThat(merged.resolvedSources()).doesNotContain(BookFieldSource.HARDCOVER);
    }

    @Test
    void shouldKeepIsbnHitWithMatchingTitle() {
        final SourceCollector collector = collectorWithIsbnHit(DELUXE_TITLE, HARDCOVER_RATING);

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
    }

    @Test
    void shouldKeepIsbnHitWithNoTitle() {
        final SourceBook hit = SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .averageRating(HARDCOVER_RATING)
            .build();
        final SourceCollector collector =
            collectorWith(SAME_THREAD, stubByIsbn(BookFieldSource.HARDCOVER, ISBN, hit));

        final MergedBook merged = collector.collectFor(ISBN_SEED);

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
    }

    @Test
    void shouldKeepIsbnHitWhenSeedHasNoTitle() {
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .build();
        final SourceCollector collector = collectorWithIsbnHit(SEQUEL_TITLE, HARDCOVER_RATING);

        final MergedBook merged = collector.collectFor(seed);

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
    }

    @Test
    void shouldKeepTitleLookupHitWithAnotherTitle() {
        final SourceCollector collector = collectorWith(SAME_THREAD, stubByTitleAuthor(
            BookFieldSource.HARDCOVER, TITLE, AUTHOR, hardcoverHit(SEQUEL_TITLE, HARDCOVER_RATING)));

        final MergedBook merged = collector.collectFor(WORK_KEY_SEED);

        assertThat(merged.book().averageRating()).isEqualTo(HARDCOVER_RATING);
    }

    @Test
    void shouldWarnWhenIsbnHitIsDropped(final CapturedOutput output) {
        final String brokenIsbn = ISBN + "\n";
        final SourceBook seed = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(brokenIsbn)
            .title("Du\r\nne")
            .build();
        final SourceCollector collector = collectorWith(SAME_THREAD, stubByIsbn(
            BookFieldSource.HARDCOVER, brokenIsbn, hardcoverHit("Dune\n\u001B  Messiah", WRONG_RATING)));

        collector.collectFor(seed);

        assertThat(output.getOut().lines())
            .filteredOn(line -> line.contains(DROPPED_HIT))
            .singleElement()
            .asString()
            .contains(" WARN ", DROPPED_HIT + " source=HARDCOVER isbn=" + ISBN
                + " seedTitle=\"Dune\" hitTitle=\"DuneMessiah\"");
    }

    @Test
    void shouldNotWarnWhenIsbnHitHasMatchingTitle(final CapturedOutput output) {
        final SourceCollector collector = collectorWithIsbnHit(DELUXE_TITLE, HARDCOVER_RATING);

        collector.collectFor(ISBN_SEED);

        assertThat(output.getOut()).doesNotContain(DROPPED_HIT);
    }

    private static SourceBook hardcoverHit(final String title, final double rating) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .isbn13(ISBN)
            .title(title)
            .averageRating(rating)
            .build();
    }

    private static SourceCollector collectorWithIsbnHit(final String title, final double rating) {
        return collectorWith(SAME_THREAD, stubByIsbn(BookFieldSource.HARDCOVER, ISBN, hardcoverHit(title, rating)));
    }

    private static @Nullable List<String> awardsFromWikidataTitleAuthorHit(final SourceBook seed) {
        final SourceBook wikidataHit = SourceBook.builder(BookFieldSource.WIKIDATA)
            .title(TITLE)
            .awards(List.of(HUGO))
            .build();
        final SourceCollector collector =
            collectorWith(SAME_THREAD, stubByTitleAuthor(BookFieldSource.WIKIDATA, TITLE, AUTHOR, wikidataHit));
        final MergedBook merged = collector.collectFor(seed);
        return merged.book().awards();
    }

    private static SourceCollector collectorWith(final Executor executor, final BookSourceClient... clients) {
        return new SourceCollector(new SourceMerger(), List.of(clients), new DescriptionSelector(List.of()), executor);
    }
}
