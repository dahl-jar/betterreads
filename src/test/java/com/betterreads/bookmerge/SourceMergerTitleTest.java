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

    private static final String TOO_LONG_TITLE = "x".repeat(501);

    private static final String EDITION_TAG = " (Deluxe Edition)";

    private static final String AUTHOR_NAME = "Author Name";

    private static final String HOBBIT = "The Hobbit";

    private static final String LANGUAGE_ENGLISH = "en";

    private final SourceMerger merger = new SourceMerger();

    private static SourceBook.Builder titled(final BookFieldSource source, final String title) {
        return SourceBook.builder(source).title(title);
    }

    private static SourceBook.Builder google(final String title) {
        return titled(BookFieldSource.GOOGLE_BOOKS, title);
    }

    private MergedBook merge(final SourceBook.Builder top) {
        return merger.merge(null, List.of(top.build()));
    }

    private MergedBook merge(final SourceBook.Builder top, final String openLibraryTitle) {
        return merger.merge(null, List.of(top.build(), titled(BookFieldSource.OPEN_LIBRARY, openLibraryTitle).build()));
    }

    @Nested
    class TitleChoice {

        @Test
        void shouldPreferCleanTitleOverAllCapsTopSource() {
            final MergedBook merged = merge(google(ALL_CAPS_KING), CLEAN_KING);

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        void shouldPreferCleanTitleOverTooLongTopSource() {
            final MergedBook merged = merge(google(TOO_LONG_TITLE), CLEAN_KING);

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        void shouldFixCaseWhenEverySourceIsAllCaps() {
            final MergedBook merged = merge(google(ALL_CAPS_KING).language(LANGUAGE_ENGLISH), ALL_CAPS_KING);

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        }

        @Test
        void shouldKeepSingleWordTitleOverOtherCasing() {
            final String singleWord = "JLA";

            final MergedBook merged = merge(google(singleWord), "Jla");

            assertThat(merged.book().title()).isEqualTo(singleWord);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        }

        @Test
        void shouldKeepAllCapsTitleOfFrenchBook() {
            final String frenchTitle = "LES MISÉRABLES";

            final MergedBook merged = merge(google(frenchTitle).language("fr"));

            assertThat(merged.book().title()).isEqualTo(frenchTitle);
        }

        @Test
        void shouldFixCaseAfterEditionTagIsRemoved() {
            final MergedBook merged = merge(google(ALL_CAPS_KING + EDITION_TAG).language(LANGUAGE_ENGLISH));

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
        }

        @Test
        void shouldSkipAllCapsTitleBehindEditionTag() {
            final MergedBook merged = merge(google(ALL_CAPS_KING + EDITION_TAG), CLEAN_KING);

            assertThat(merged.book().title()).isEqualTo(CLEAN_KING);
        }
    }

    @Nested
    class Byline {

        private static final String AUTHOR_BYLINE = "The Hobbit by Author Name";

        private static final List<SourceAuthor> AUTHOR_CREDIT = List.of(SourceAuthor.ofName(AUTHOR_NAME));

        @Test
        void shouldPreferCleanTitleOverOwnAuthorByline() {
            final MergedBook merged = merge(google(AUTHOR_BYLINE).authors(AUTHOR_CREDIT), HOBBIT);

            assertThat(merged.book().title()).isEqualTo(HOBBIT);
            assertThat(merged.provenanceOf(BookField.TITLE)).isEqualTo(BookFieldSource.OPEN_LIBRARY);
        }

        @Test
        void shouldMatchBylineAgainstCreditsOfAnySource() {
            final SourceBook hardcover = titled(BookFieldSource.HARDCOVER, HOBBIT).authors(AUTHOR_CREDIT).build();

            final MergedBook merged = merger.merge(null, List.of(google(AUTHOR_BYLINE).build(), hardcover));

            assertThat(merged.book().title()).isEqualTo(HOBBIT);
        }

        @Test
        void shouldIgnoreIllustratorForByline() {
            final List<SourceAuthor> illustrator = List.of(SourceAuthor.withRole(AUTHOR_NAME, CreditRole.ILLUSTRATOR));

            final MergedBook merged = merge(google(AUTHOR_BYLINE).authors(illustrator), HOBBIT);

            assertThat(merged.book().title()).isEqualTo(AUTHOR_BYLINE);
        }

        @Test
        void shouldKeepOwnAuthorBylineWhenNoOtherTitle() {
            final MergedBook merged = merge(google(AUTHOR_BYLINE).authors(AUTHOR_CREDIT));

            assertThat(merged.book().title()).isEqualTo(AUTHOR_BYLINE);
        }

        @Test
        void shouldSkipBylineBehindEditionTag() {
            final MergedBook merged = merge(google(AUTHOR_BYLINE + EDITION_TAG).authors(AUTHOR_CREDIT), HOBBIT);

            assertThat(merged.book().title()).isEqualTo(HOBBIT);
        }
    }
}
