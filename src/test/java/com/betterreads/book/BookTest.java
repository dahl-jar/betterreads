package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        @DisplayName("a re-apply with null subjects keeps the existing ones")
        void nullSubjectsPreserveExisting() {
            final Book book = new Book();
            book.applyFrom(sourceWithSubjects(List.of(FANTASY, FICTION)));

            book.applyFrom(sourceWithSubjects(null));

            assertThat(book.getSubjects())
                .as("null subjects mean the source did not return the field, so a refresh keeps the stored genres")
                .extracting(BookSubject::getSubject)
                .containsExactly(FANTASY, FICTION);
        }

        @Test
        @DisplayName("a re-apply with an empty list clears subjects")
        void emptySubjectsClearExisting() {
            final Book book = new Book();
            book.applyFrom(sourceWithSubjects(List.of(FANTASY)));

            book.applyFrom(sourceWithSubjects(List.of()));

            assertThat(book.getSubjects())
                .as("an empty list clears the stored genres")
                .isEmpty();
        }

        @Test
        @DisplayName("a re-apply with new subjects replaces the old ones")
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
        @DisplayName("a later source without an LCCN keeps the stored one")
        void laterSourceWithoutLccnPreservesIt() {
            final Book book = new Book();
            book.applyFrom(locSource(DUNE_LCCN));

            book.applyFrom(sourceWithSubjects(null));

            assertThat(book.getLocLccn())
                .as("a Google or OL refresh without an LCCN keeps the LoC one")
                .isEqualTo(DUNE_LCCN);
        }
    }

    @Nested
    @DisplayName("applySeries")
    class ApplySeries {

        private static final String SERIES = "The Sun Eater";

        private static final String UMBRELLA = "The Sollan Empire";

        private static final int UMBRELLA_VOLUME = 4;

        @Test
        void shouldMakeTheFirstSeriesThePrimary() {
            final Book book = new Book();

            book.applySeries(List.of(new SeriesEntry(SERIES, 1), new SeriesEntry(UMBRELLA, UMBRELLA_VOLUME)), true);

            assertThat(book).satisfies(applied -> {
                assertThat(applied.getSeriesName()).isEqualTo(SERIES);
                assertThat(applied.getSeriesPosition()).isEqualTo(1);
            });
        }

        @Test
        void shouldKeepEverySeriesInOrder() {
            final Book book = new Book();

            book.applySeries(List.of(new SeriesEntry(SERIES, 1), new SeriesEntry(UMBRELLA, UMBRELLA_VOLUME)), true);

            assertThat(book.getSeries())
                .containsExactly(new SeriesEntry(SERIES, 1), new SeriesEntry(UMBRELLA, UMBRELLA_VOLUME));
        }

        @Test
        @DisplayName("a resolved authority with no series clears an existing label")
        void resolvedEmptyClearsExisting() {
            final Book book = new Book();
            book.applySeries(List.of(new SeriesEntry(SERIES, 1)), true);

            book.applySeries(List.of(), true);

            assertThat(book).satisfies(applied -> {
                assertThat(applied.getSeriesName())
                    .as("the authority resolved and reported no volume, so the stale label is cleared")
                    .isNull();
                assertThat(applied.getSeriesPosition()).isNull();
                assertThat(applied.getSeries()).isEmpty();
            });
        }

        @Test
        @DisplayName("an unresolved authority keeps the existing label, so a transient miss does not wipe it")
        void unresolvedEmptyKeepsExisting() {
            final Book book = new Book();
            book.applySeries(List.of(new SeriesEntry(SERIES, 2)), true);

            book.applySeries(List.of(), false);

            assertThat(book).satisfies(applied -> {
                assertThat(applied.getSeriesName())
                    .as("no series authority resolved this run, so the existing series is kept")
                    .isEqualTo(SERIES);
                assertThat(applied.getSeriesPosition()).isEqualTo(2);
            });
        }
    }

    @Nested
    class ApplyFrom {

        private static final String TITLE = "Red Rising";
        private static final String SUBTITLE = "Book One";
        private static final String DESCRIPTION = "Darrow is a Helldiver in the mines of Mars.";
        private static final String COVER_URL = "https://covers.example.test/red-rising.jpg";
        private static final int YEAR = 2014;
        private static final String ISBN = "9780345539786";
        private static final int PAGES = 382;
        private static final String LANGUAGE = "en";
        private static final String AWARD = "Hugo Award";
        private static final String GOOGLE_ID = "gb-1";
        private static final String WORK_KEY = "OL2W";
        private static final String HARDCOVER_ID = "hc-1";
        private static final String QID = "Q1";
        private static final double RATING = 4.316;
        private static final String ROUNDED_RATING = "4.32";
        private static final int RATING_COUNT = 9000;

        @Test
        void shouldCopyEverySourceFieldOntoBook() {
            final SourceBook redRising = SourceBook.builder(BookFieldSource.HARDCOVER)
                .title(TITLE)
                .subtitle(SUBTITLE)
                .description(DESCRIPTION)
                .coverUrl(COVER_URL)
                .publicationYear(YEAR)
                .isbn13(ISBN)
                .pageCount(PAGES)
                .language(LANGUAGE)
                .awards(List.of(AWARD))
                .googleBooksVolumeId(GOOGLE_ID)
                .openLibraryWorkKey(WORK_KEY)
                .hardcoverId(HARDCOVER_ID)
                .locLccn(DUNE_LCCN)
                .wikidataQid(QID)
                .averageRating(RATING)
                .ratingCount(RATING_COUNT)
                .build();
            final Book book = new Book();

            book.applyFrom(redRising);

            assertThat(book)
                .extracting(
                    Book::getTitle, Book::getSubtitle, Book::getDescription, Book::getCoverUrl,
                    Book::getFirstPublishYear, Book::getIsbn, Book::getPageCount, Book::getLanguage,
                    Book::getGoogleBooksVolumeId, Book::getOpenLibraryWorkKey, Book::getHardcoverId,
                    Book::getLocLccn, Book::getWikidataQid, Book::getAverageRating, Book::getRatingCount,
                    Book::getDedupKey)
                .containsExactly(
                    TITLE, SUBTITLE, DESCRIPTION, COVER_URL, YEAR, ISBN, PAGES, LANGUAGE,
                    GOOGLE_ID, WORK_KEY, HARDCOVER_ID, DUNE_LCCN, QID, new BigDecimal(ROUNDED_RATING),
                    RATING_COUNT, ISBN);
            assertThat(book.getAwards()).extracting(BookAward::getAward).containsExactly(AWARD);
        }

        @Test
        void shouldKeepDedupKeyWhenLaterSourceAddsIsbn() {
            final SourceBook workOnly = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .openLibraryWorkKey(WORK_KEY)
                .title(TITLE)
                .build();
            final SourceBook withIsbn = workOnly.toBuilder()
                .isbn13(ISBN)
                .build();
            final Book book = new Book();
            book.applyFrom(workOnly);

            book.applyFrom(withIsbn);

            assertThat(book.getDedupKey()).isEqualTo(WORK_KEY);
        }

        @Test
        void shouldRejectSourceWithoutIdentifier() {
            final SourceBook noIds = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .title(TITLE)
                .build();
            final Book book = new Book();

            assertThatThrownBy(() -> book.applyFrom(noIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source identifier");
        }

        @Test
        void shouldRejectSourceWithoutTitle() {
            final SourceBook noTitle = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
                .openLibraryWorkKey(WORK_KEY)
                .build();
            final Book book = new Book();

            assertThatThrownBy(() -> book.applyFrom(noTitle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no title");
        }
    }

    @Nested
    class Verified {

        private static final String GOLDEN_SON = "Golden Son";

        private static final String RED_RISING_SAGA = "Red Rising Saga";

        private static final int YEAR = 2015;

        private static final int WRONG_YEAR = 1999;

        private static final String ENGLISH_ISBN = "9780345539816";

        private static final String GERMAN_ISBN = "9783608949650";

        private static final String BLURB = "Darrow infiltrates the Golds.";

        private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

        private static VerifiedMetadata verifiedTitle(final String title) {
            return new VerifiedMetadata(title, null, null, null, null, null, null);
        }

        private static SourceBook refresh(final int year) {
            return SourceBook.builder(BookFieldSource.LOC).locLccn(DUNE_LCCN).title(A_TITLE).publicationYear(year)
                .build();
        }

        @Test
        void shouldKeepVerifiedTitle() {
            final Book book = new Book();
            book.applyFrom(refresh(YEAR));
            book.applyVerified(verifiedTitle(GOLDEN_SON), NOW);

            book.applyFrom(refresh(YEAR));

            assertThat(book.getTitle()).isEqualTo(GOLDEN_SON);
        }

        @Test
        void shouldKeepVerifiedFieldsOnRefresh() {
            final Book book = new Book();
            book.applyFrom(refresh(YEAR));
            book.applyVerified(new VerifiedMetadata(null, null, YEAR, null, null, BLURB, ENGLISH_ISBN), NOW);

            book.applyFrom(SourceBook.builder(BookFieldSource.LOC).locLccn(DUNE_LCCN).title(A_TITLE)
                .publicationYear(WRONG_YEAR).description("Another blurb.").isbn13(GERMAN_ISBN).language("de")
                .build());

            assertThat(book.getFirstPublishYear()).isEqualTo(YEAR);
            assertThat(book.getDescription()).isEqualTo(BLURB);
            assertThat(book.getIsbn()).isEqualTo(ENGLISH_ISBN);
            assertThat(book.getLanguage()).isEqualTo("en");
        }

        @Test
        void shouldUpdateUnverifiedYear() {
            final Book book = new Book();
            book.applyFrom(refresh(YEAR));
            book.applyVerified(verifiedTitle(GOLDEN_SON), NOW);

            book.applyFrom(refresh(WRONG_YEAR));

            assertThat(book.getFirstPublishYear()).isEqualTo(WRONG_YEAR);
        }

        @Test
        void shouldKeepVerifiedSeries() {
            final Book book = new Book();
            book.applyFrom(refresh(YEAR));
            book.applySeries(
                List.of(new SeriesEntry("Red Rising Trilogy", 1), new SeriesEntry(RED_RISING_SAGA, 1)), true);
            book.applyVerified(new VerifiedMetadata(null, null, null, RED_RISING_SAGA, 2, null, null), NOW);

            book.applySeries(List.of(new SeriesEntry("Red Rising (German)", 1)), true);

            assertThat(book.getSeries()).containsExactly(new SeriesEntry(RED_RISING_SAGA, 2));
            assertThat(book.getSeriesName()).isEqualTo(RED_RISING_SAGA);
            assertThat(book.getSeriesPosition()).isEqualTo(2);
        }

        @Test
        void shouldMarkAuthorsVerified() {
            final Book book = new Book();

            book.applyVerified(new VerifiedMetadata(null, List.of("Darrow"), null, null, null, null, null), NOW);

            assertThat(book.getVerifiedFields()).containsExactly(VerifiedField.AUTHORS);
        }

        @Test
        void shouldSetEnglishOnIsbnSwap() {
            final Book book = new Book();
            book.applyFrom(refresh(YEAR));
            book.setIsbn(GERMAN_ISBN);

            book.applyVerified(new VerifiedMetadata(null, null, null, null, null, null, ENGLISH_ISBN), NOW);

            assertThat(book.getIsbn()).isEqualTo(ENGLISH_ISBN);
            assertThat(book.getLanguage()).isEqualTo("en");
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
