package com.betterreads.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Book} subject handling, source-id accrual, and the guarded series write. */
class BookTest {

    private static final String FANTASY = "fantasy";

    private static final String FICTION = "fiction";

    private static final String CLASSICS = "classics";

    private static final String DUNE_LCCN = "2019287107";

    private static final String A_TITLE = "A Title";

    @Nested
    @DisplayName("applyFrom subject replacement")
    class Subjects {

        @Test
        @DisplayName("a source with subjects sets them")
        void setsSubjectsFromSource() {
            final Book book = new Book();
            book.applyFrom(sourceWithSubjects(List.of(FANTASY, FICTION)));

            assertThat(book.getSubjects())
                .extracting(BookSubject::getSubject)
                .containsExactly(FANTASY, FICTION);
        }

        @Test
        @DisplayName("a re-apply with null subjects PRESERVES the existing ones (field missing != cleared)")
        void nullSubjectsPreserveExisting() {
            final Book book = new Book();
            book.applyFrom(sourceWithSubjects(List.of(FANTASY, FICTION)));

            book.applyFrom(sourceWithSubjects(null));

            assertThat(book.getSubjects())
                .as("null subjects mean the source did not return the field (work-detail 4xx), so a "
                    + "refresh must keep previously stored genres, not delete them")
                .extracting(BookSubject::getSubject)
                .containsExactly(FANTASY, FICTION);
        }

        @Test
        @DisplayName("a re-apply with an empty list CLEARS subjects (field present, no values)")
        void emptySubjectsClearExisting() {
            final Book book = new Book();
            book.applyFrom(sourceWithSubjects(List.of(FANTASY)));

            book.applyFrom(sourceWithSubjects(List.of()));

            assertThat(book.getSubjects())
                .as("an empty list is an explicit 'no genres', distinct from null; it replaces")
                .isEmpty();
        }

        @Test
        @DisplayName("a re-apply with new subjects replaces rather than appends")
        void newSubjectsReplace() {
            final Book book = new Book();
            book.applyFrom(sourceWithSubjects(List.of(FANTASY, FICTION)));

            book.applyFrom(sourceWithSubjects(List.of(CLASSICS)));

            assertThat(book.getSubjects())
                .extracting(BookSubject::getSubject)
                .containsExactly(CLASSICS);
        }
    }

    @Nested
    @DisplayName("applyFrom LCCN accrual")
    class LocLccn {

        @Test
        @DisplayName("a LoC source sets the loc_lccn")
        void setsLccnFromSource() {
            final Book book = new Book();
            book.applyFrom(locSource(DUNE_LCCN));

            assertThat(book.getLocLccn()).isEqualTo(DUNE_LCCN);
        }

        @Test
        @DisplayName("a later source without an LCCN keeps the stored one")
        void laterSourceWithoutLccnPreservesIt() {
            final Book book = new Book();
            book.applyFrom(locSource(DUNE_LCCN));

            book.applyFrom(sourceWithSubjects(null));

            assertThat(book.getLocLccn())
                .as("a Google or OL refresh that carries no LCCN must not wipe the LoC one")
                .isEqualTo(DUNE_LCCN);
        }
    }

    @Nested
    @DisplayName("applySeries")
    class ApplySeries {

        private static final String SERIES = "The Sun Eater";

        @Test
        @DisplayName("a resolved authority with a numbered volume sets the name and position")
        void resolvedVolumeIsSet() {
            final Book book = new Book();

            book.applySeries(SERIES, 1, true);

            assertThat(book).satisfies(applied -> {
                assertThat(applied.getSeriesName()).isEqualTo(SERIES);
                assertThat(applied.getSeriesPosition()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("a resolved authority with no series clears an existing label")
        void resolvedNullClearsExisting() {
            final Book book = new Book();
            book.applySeries(SERIES, 1, true);

            book.applySeries(null, null, true);

            assertThat(book).satisfies(applied -> {
                assertThat(applied.getSeriesName())
                    .as("the authority resolved and reported no volume, so the stale label is cleared")
                    .isNull();
                assertThat(applied.getSeriesPosition()).isNull();
            });
        }

        @Test
        @DisplayName("an unresolved authority keeps the existing label, so a transient miss does not wipe it")
        void unresolvedNullKeepsExisting() {
            final Book book = new Book();
            book.applySeries(SERIES, 2, true);

            book.applySeries(null, null, false);

            assertThat(book).satisfies(applied -> {
                assertThat(applied.getSeriesName())
                    .as("no series authority resolved this run, so the existing series is kept")
                    .isEqualTo(SERIES);
                assertThat(applied.getSeriesPosition()).isEqualTo(2);
            });
        }
    }

    private static SourceBook sourceWithSubjects(final @Nullable List<String> subjects) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL1W")
            .title(A_TITLE)
            .rawSubjects(subjects)
            .build();
    }

    private static SourceBook locSource(final String lccn) {
        return SourceBook.builder(BookFieldSource.LOC)
            .locLccn(lccn)
            .title(A_TITLE)
            .build();
    }
}
