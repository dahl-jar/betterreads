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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Unit tests for {@link DescriptionSelector}. After the merge, the selector consults the
 * description-only sources for a stronger blurb, keeping the highest-quality one. The merge's own
 * description competes; a source only wins when it scores higher.
 */
class DescriptionSelectorTest {

    private static final String WEAK =
        "A short merged blurb about a desert planet and the boy who would rule it, just over the bar.";

    private static final String STRONG =
        "Dune is a 1965 epic science fiction novel by Frank Herbert, set on the desert planet "
        + "Arrakis, the only source of the spice melange. It follows the rise of Paul Atreides as "
        + "his family takes stewardship of the planet and is betrayed into the deep desert.";

    private static final String STUB = "A tiny stub.";

    private static final String QID = "Q190192";

    private static final String ISBN = "9780441013593";

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

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
        final DescriptionSelector selector = new DescriptionSelector(List.of(source("<p>" + STRONG + "</p>")));
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
    @DisplayName("the lookup carries the book's qid, isbn, title, and author")
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
    }

    @Test
    @DisplayName("the winning source is recorded as the description provenance")
    void recordsWinningSourceProvenance() {
        final DescriptionSelector selector = new DescriptionSelector(List.of(source(STRONG)));
        final MergedBook merged = mergedWith(WEAK);

        final MergedBook selected = selector.withBestDescription(merged);

        assertThat(selected.provenanceOf(BookField.DESCRIPTION)).isEqualTo(BookFieldSource.WIKIPEDIA);
    }

    @Test
    @DisplayName("provenance is unchanged when no source beats the merged description")
    void keepsProvenanceWhenNoSourceWins() {
        final DescriptionSelector selector = new DescriptionSelector(List.of(source(STUB)));
        final MergedBook merged = mergedWith(STRONG);

        final MergedBook selected = selector.withBestDescription(merged);

        assertThat(selected.provenanceOf(BookField.DESCRIPTION)).isNull();
    }

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

    private static MergedBook mergedWith(final String description) {
        final SourceBook book = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .title(TITLE)
            .description(description)
            .isbn13(ISBN)
            .wikidataQid(QID)
            .authors(List.of(SourceAuthor.ofName(AUTHOR)))
            .build();
        return new MergedBook(book, Map.of(BookField.TITLE, BookFieldSource.GOOGLE_BOOKS),
            Set.of(), Set.of(BookFieldSource.GOOGLE_BOOKS));
    }

    private static DescriptionSource source(final String description) {
        return new DescriptionSource() {
            @Override
            public BookFieldSource source() {
                return BookFieldSource.WIKIPEDIA;
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
