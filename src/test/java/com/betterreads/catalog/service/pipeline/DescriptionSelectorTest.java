package com.betterreads.catalog.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.betterreads.catalog.service.source.BookField;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.DescriptionSource;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Unit tests for {@link DescriptionSelector}. The selector consults the description-only sources
 * for a stronger blurb, keeping the highest-quality one; the current description competes, so a
 * source only wins on a higher score. A fallback-only source is consulted solely when the current
 * description is unusable and no other source supplied a usable one.
 */
class DescriptionSelectorTest {

    private static final String WEAK =
        "A short merged blurb about a desert planet and the boy who would rule it, just over the bar.";

    private static final String STRONG =
        "On the desert planet Arrakis, the only source of the spice melange, Paul Atreides watches "
        + "his family take stewardship of the planet, is betrayed into the deep desert, and rises "
        + "among the Fremen to reclaim what was taken from his house.";

    private static final String STUB = "A tiny stub.";

    private static final String FRENCH =
        "Ils m'appellent Vis Telimus. Ils croient que j'ai eu la chance d'être adopté par un "
        + "sénateur et envoyé à l'Académie pour rejoindre l'élite. Celle-ci exploite l'énergie "
        + "mentale des castes inférieures, leur Volonté, pour se doter de talents extraordinaires. "
        + "Ainsi la Hiérarchie a-t-elle conquis le monde.";

    private static final String QID = "Q190192";

    private static final String ISBN = "9780441013593";

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final String WORK_KEY = "OL893415W";

    private static final String HARDCOVER_ID = "292120";

    @Nested
    @DisplayName("selection")
    class Selection {

        @Test
        @DisplayName("a stronger source description replaces the weaker merged one")
        void strongerSourceWins() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(STRONG)));
            final MergedBook merged = mergedWith(WEAK);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
        }

        @Test
        @DisplayName("the merged description is kept when no source beats it")
        void keepsMergedWhenSourcesAreWeaker() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(STUB)));
            final MergedBook merged = mergedWith(STRONG);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
        }

        @Test
        @DisplayName("a source description is cleaned of markup before it is stored")
        void cleansSourceDescription() {
            final DescriptionSelector selector =
                new DescriptionSelector(List.of(source("<p>" + STRONG + "</p>")));
            final MergedBook merged = mergedWith(WEAK);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
        }

        @Test
        @DisplayName("a book with no description gains one from a source")
        void fillsMissingDescription() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(STRONG)));
            final MergedBook merged = mergedWith(null);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
        }

        @Test
        @DisplayName("a non-English description is replaced by a weaker-scoring English one")
        void nonEnglishDescriptionLosesToEnglish() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(WEAK)));
            final MergedBook merged = mergedWith(FRENCH);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(WEAK);
        }
    }

    @Nested
    @DisplayName("fallback tier")
    class FallbackTier {

        @Test
        @DisplayName("a fallback source never replaces a usable description, even with a higher score")
        void fallbackNeverReplacesUsableDescription() {
            final DescriptionSelector selector =
                new DescriptionSelector(List.of(fallbackSource(STRONG)));
            final MergedBook merged = mergedWith(WEAK);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(WEAK);
        }

        @Test
        @DisplayName("a fallback source fills in when the description is unusable and no other source has one")
        void fallbackFillsWhenNothingElseIsUsable() {
            final DescriptionSelector selector =
                new DescriptionSelector(List.of(source(null), fallbackSource(STRONG)));
            final MergedBook merged = mergedWith(null);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
            assertThat(selected.provenanceOf(BookField.DESCRIPTION)).isEqualTo(BookFieldSource.WIKIPEDIA);
        }
    }

    @Nested
    @DisplayName("lookup and provenance")
    class LookupAndProvenance {

        @Test
        @DisplayName("the merge-time lookup carries qid, isbn, title, and author, but no refetch ids")
        void passesIdentifiersToSources() {
            final CapturingSource capturing = new CapturingSource();
            final DescriptionSelector selector = new DescriptionSelector(List.of(capturing));

            selector.withBestDescription(mergedWith(WEAK));

            final DescriptionLookup seen = capturing.seen.get();
            assertThat(seen).isNotNull();
            assertThat(seen.wikidataQid()).isEqualTo(QID);
            assertThat(seen.isbn13()).isEqualTo(ISBN);
            assertThat(seen.title()).isEqualTo(TITLE);
            assertThat(seen.author()).isEqualTo(AUTHOR);
            assertThat(seen.openLibraryWorkKey())
                .as("the collect already fetched OpenLibrary; the selector must not key a refetch")
                .isNull();
            assertThat(seen.hardcoverId())
                .as("the collect already fetched Hardcover; the selector must not key a refetch")
                .isNull();
        }

        @Test
        @DisplayName("the winning source is recorded as the description provenance")
        void recordsWinningSourceProvenance() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(STRONG)));
            final MergedBook merged = mergedWith(WEAK);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.provenanceOf(BookField.DESCRIPTION)).isEqualTo(BookFieldSource.ITUNES);
        }

        @Test
        @DisplayName("provenance is unchanged when no source beats the merged description")
        void keepsProvenanceWhenNoSourceWins() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(STUB)));
            final MergedBook merged = mergedWith(STRONG);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.provenanceOf(BookField.DESCRIPTION)).isNull();
        }
    }

    @Nested
    @DisplayName("self clean")
    class SelfClean {

        @Test
        @DisplayName("a stored description with a marketing lead is rewritten as its own cleaned form")
        void rewritesStoredDescriptionAsItsCleanedForm() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(null)));
            final String marketingLed =
                "From #1 New York Times bestselling author Frank Herbert comes the classic. " + STRONG;

            final Optional<String> best = selector.bestDescription(lookup(), marketingLed);

            assertThat(best).contains(STRONG);
        }

        @Test
        @DisplayName("an already-clean stored description is left alone")
        void leavesCleanStoredDescriptionAlone() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(source(null)));

            final Optional<String> best = selector.bestDescription(lookup(), STRONG);

            assertThat(best)
                .as("rewriting an unchanged text would churn every row on every sweep")
                .isEmpty();
        }

        private DescriptionLookup lookup() {
            return new DescriptionLookup(QID, ISBN, TITLE, AUTHOR, WORK_KEY, HARDCOVER_ID);
        }
    }

    @Nested
    @DisplayName("failing sources")
    class FailingSources {

        @Test
        @DisplayName("a source that fails is skipped and a working source still wins")
        void failingSourceIsSkipped() {
            final DescriptionSelector selector =
                new DescriptionSelector(List.of(failingSource(), source(STRONG)));
            final MergedBook merged = mergedWith(WEAK);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
        }

        @Test
        @DisplayName("the merged description is kept when the only source fails")
        void keepsMergedWhenOnlySourceFails() {
            final DescriptionSelector selector = new DescriptionSelector(List.of(failingSource()));
            final MergedBook merged = mergedWith(STRONG);

            final MergedBook selected = selector.withBestDescription(merged);

            assertThat(selected.book().description()).isEqualTo(STRONG);
        }
    }

    private static MergedBook mergedWith(final String description) {
        final SourceBook book = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .title(TITLE)
            .description(description)
            .isbn13(ISBN)
            .wikidataQid(QID)
            .openLibraryWorkKey(WORK_KEY)
            .hardcoverId(HARDCOVER_ID)
            .authors(List.of(SourceAuthor.ofName(AUTHOR)))
            .build();
        return new MergedBook(book, Map.of(BookField.TITLE, BookFieldSource.GOOGLE_BOOKS),
            Set.of(), Set.of(BookFieldSource.GOOGLE_BOOKS));
    }

    private static DescriptionSource source(final String description) {
        return new DescriptionSource() {
            @Override
            public BookFieldSource source() {
                return BookFieldSource.ITUNES;
            }

            @Override
            public Optional<String> fetch(final DescriptionLookup lookup) {
                return Optional.ofNullable(description);
            }
        };
    }

    private static DescriptionSource fallbackSource(final String description) {
        return new DescriptionSource() {
            @Override
            public BookFieldSource source() {
                return BookFieldSource.WIKIPEDIA;
            }

            @Override
            public boolean fallbackOnly() {
                return true;
            }

            @Override
            public Optional<String> fetch(final DescriptionLookup lookup) {
                return Optional.ofNullable(description);
            }
        };
    }

    private static DescriptionSource failingSource() {
        return new DescriptionSource() {
            @Override
            public BookFieldSource source() {
                return BookFieldSource.ITUNES;
            }

            @Override
            public Optional<String> fetch(final DescriptionLookup lookup) {
                throw new WebClientResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", null, null, null);
            }
        };
    }

    private static final class CapturingSource implements DescriptionSource {
        private final AtomicReference<DescriptionLookup> seen = new AtomicReference<>();

        @Override
        public BookFieldSource source() {
            return BookFieldSource.ITUNES;
        }

        @Override
        public Optional<String> fetch(final DescriptionLookup lookup) {
            seen.set(lookup);
            return Optional.empty();
        }
    }
}
