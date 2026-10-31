package com.betterreads.bookmerge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.booksource.BookField;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CreditRole;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SourceMergerTitleTest {

    private static final String ALL_CAPS_KING = "RETURN OF THE KING";

    private static final String CLEAN_KING = "Return of the King";

    private static final String TOLKIEN = "J.R.R. Tolkien";

    private static final String HOBBIT = "The Hobbit";

    private static final String OTHER_AUTHOR = "Other Author";

    private static final String LANGUAGE_ENGLISH = "en";

    private final SourceMerger merger = new SourceMerger();

    private static SourceBook.Builder titled(final BookFieldSource source, final String title) {
        return SourceBook.builder(source).title(title);
    }

    @Nested
    class TitleChoice {

        @Test
        void shouldPreferCleanTitleOverAllCapsTopSource() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, ALL_CAPS_KING).build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, CLEAN_KING).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        void shouldFixCaseWhenEverySourceIsAllCaps() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, ALL_CAPS_KING)
                .language(LANGUAGE_ENGLISH)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, ALL_CAPS_KING).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        }

        @Test
        void shouldKeepSingleWordTitleOverOtherCasing() {
            final String singleWord = "JLA";
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, singleWord).build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, "Jla").build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(singleWord);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        }

        @Test
        void shouldKeepTitleWithoutLettersFromTopSource() {
            final String dates = "1984 2001";
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, dates).build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, "Two Dates").build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(dates);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        }

        @Test
        void shouldPreferCleanCyrillicTitle() {
            final String cleanTitle = "Война и мир";
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, "ВОЙНА И МИР").language("ru").build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, cleanTitle).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(cleanTitle);
        }

        @Test
        void shouldKeepAllCapsTitleOfFrenchBook() {
            final String frenchTitle = "LES MISÉRABLES";
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, frenchTitle).language("fr").build();

            final MergedBook merged = merger.merge(null, List.of(google));

            assertThat(merged.book().title()).isEqualTo(frenchTitle);
        }

        @Test
        void shouldFixCaseAfterEditionTagIsRemoved() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, "RETURN OF THE KING (Deluxe Edition)")
                .language(LANGUAGE_ENGLISH)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google));

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
        }
    }

    @Nested
    class Byline {

        private static final String TOLKIEN_BYLINE = "The Hobbit by J.R.R. Tolkien";

        private static final List<SourceAuthor> TOLKIEN_CREDIT = List.of(SourceAuthor.ofName(TOLKIEN));

        @Test
        void shouldPreferCleanTitleOverOwnAuthorByline() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, TOLKIEN_BYLINE)
                .authors(TOLKIEN_CREDIT)
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, HOBBIT).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(HOBBIT);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        void shouldMatchBylineAgainstCreditsOfAnySource() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, TOLKIEN_BYLINE).build();
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER, HOBBIT)
                .authors(TOLKIEN_CREDIT)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google, hardcover));

            assertThat(merged.book().title()).isEqualTo(HOBBIT);
        }

        @Test
        void shouldKeepBylineOfSomeoneElse() {
            final String otherByline = "Murder by Agatha Christie";
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, otherByline)
                .authors(SourceAuthor.ofNames(List.of(OTHER_AUTHOR)))
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, "Murder").build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(otherByline);
        }

        @Test
        void shouldIgnoreIllustratorForByline() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, TOLKIEN_BYLINE)
                .authors(List.of(SourceAuthor.withRole(TOLKIEN, CreditRole.ILLUSTRATOR)))
                .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, HOBBIT).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(TOLKIEN_BYLINE);
        }

        @Test
        void shouldKeepOwnAuthorBylineWhenNoOtherTitle() {
            final SourceBook google = titled(BookFieldSource.GOOGLE_BOOKS, TOLKIEN_BYLINE)
                .authors(TOLKIEN_CREDIT)
                .build();

            final MergedBook merged = merger.merge(null, List.of(google));

            assertThat(merged.book().title()).isEqualTo(TOLKIEN_BYLINE);
        }

        @Test
        void shouldSkipEditionTitleWithBothIssues() {
            final SourceBook google =
                titled(BookFieldSource.GOOGLE_BOOKS, "RETURN OF THE KING BY J.R.R. TOLKIEN (Deluxe Edition)")
                    .authors(TOLKIEN_CREDIT)
                    .build();
            final SourceBook openLibrary = titled(BookFieldSource.OPEN_LIBRARY, CLEAN_KING).build();

            final MergedBook merged = merger.merge(null, List.of(google, openLibrary));

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
        }
    }
}
