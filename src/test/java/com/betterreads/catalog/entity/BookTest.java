package com.betterreads.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Book#applyFrom} subject handling. */
class BookTest {

    private static final String FANTASY = "fantasy";

    private static final String FICTION = "fiction";

    private static final String CLASSICS = "classics";

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

    private static SourceBook sourceWithSubjects(final @Nullable List<String> subjects) {
        return new SourceBook(
            BookFieldSource.OPEN_LIBRARY,
            null, "OL1W", null, null, null, null,
            "A Title", null, null, null, null, null, null, null, null,
            subjects, null,
            null, null, null, null);
    }
}
