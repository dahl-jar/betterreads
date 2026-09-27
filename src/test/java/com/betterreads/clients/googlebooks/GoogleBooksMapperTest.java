package com.betterreads.clients.googlebooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GoogleBooksMapperTest {

    private static final String TITLE = "Dune";

    private static final String ISBN_13 = "ISBN_13";

    private static final String VOLUME_ID = "gb-1";

    private static final int YEAR = 2019;

    private static final int PAGE_COUNT = 896;

    private static final String AUTHOR = "Frank Herbert";

    private static final String PUBLISHER = "Ace";

    private static final String LANGUAGE = "en";

    private static final String DESCRIPTION = "A desert planet.";

    private static final String FICTION_CATEGORY = "Fiction";

    private static final String FICTION_SUBJECT = "fiction";

    private final GoogleBooksMapper mapper = new GoogleBooksMapper();

    @Test
    void shouldMapEveryVolumeField() {
        final String subtitle = "Deluxe Edition";
        final String isbn = "9780593099322";
        final String cover = "https://books.google.com/books/content?id=gb-1";
        final GoogleBooksVolumeInfo info = new GoogleBooksVolumeInfo(
            TITLE, subtitle, List.of(AUTHOR), "2019-10-01", PUBLISHER, PAGE_COUNT, LANGUAGE,
            List.of(new IndustryIdentifier(ISBN_13, isbn)), List.of(FICTION_CATEGORY),
            "<p>" + DESCRIPTION + "</p>", new ImageLinks(cover, null));
        final GoogleBooksVolume volume = new GoogleBooksVolume(VOLUME_ID, info);

        final SourceBook book = Objects.requireNonNull(mapper.toSourceBook(volume));

        assertThat(book)
            .extracting(SourceBook::googleBooksVolumeId, SourceBook::title, SourceBook::subtitle,
                SourceBook::authorNames, SourceBook::publicationYear, SourceBook::publisher,
                SourceBook::pageCount, SourceBook::language, SourceBook::isbn13, SourceBook::rawSubjects,
                SourceBook::description, SourceBook::coverUrl)
            .containsExactly(VOLUME_ID, TITLE, subtitle, List.of(AUTHOR), YEAR, PUBLISHER, PAGE_COUNT, LANGUAGE,
                isbn, List.of(FICTION_SUBJECT), DESCRIPTION, cover);
    }

    @Test
    void shouldLeaveMissingFieldsNull() {
        final GoogleBooksVolumeInfo info = new GoogleBooksVolumeInfo(
            TITLE, null, null, null, null, null, null, null, null, null, null);
        final GoogleBooksVolume volume = new GoogleBooksVolume(VOLUME_ID, info);

        final SourceBook book = Objects.requireNonNull(mapper.toSourceBook(volume));

        assertThat(book.isbn13()).isNull();
        assertThat(book.publicationYear()).isNull();
        assertThat(book.description()).isNull();
    }

    @Test
    void shouldReturnNullWhenTheVolumeHasNoVolumeInfo() {
        final GoogleBooksVolume volume = new GoogleBooksVolume(VOLUME_ID, null);

        final SourceBook book = mapper.toSourceBook(volume);

        assertThat(book).isNull();
    }

    @Test
    void shouldLeaveCoverNullWhenImageLinksHaveNoThumbnail() {
        final SourceBook book = mapWith(info -> withImageLinks(info, new ImageLinks(null, null)));

        assertThat(book.coverUrl()).isNull();
    }

    @Nested
    @DisplayName("parseYear")
    class ParseYear {

        @Test
        @DisplayName("a date without a leading four-digit year is null")
        void garbageInputReturnsNull() {
            assertThat(GoogleBooksMapper.parseYear("circa 1990")).isNull();
        }
    }

    @Test
    @DisplayName("a page count of 0 maps to no page count")
    void nullIfZeroDropsTheReprintMarker() {
        assertThat(GoogleBooksMapper.nullIfZero(0)).isNull();
    }

    @Nested
    @DisplayName("findIsbn13")
    class FindIsbn13 {

        @Test
        @DisplayName("picks ISBN_13 when present alongside ISBN_10")
        void picksIsbn13OverIsbn10() {
            final String isbn13 = "9781250832368";
            final List<IndustryIdentifier> identifiers = List.of(
                new IndustryIdentifier("ISBN_10", "1250832365"),
                new IndustryIdentifier(ISBN_13, isbn13)
            );
            assertThat(GoogleBooksMapper.findIsbn13(identifiers)).isEqualTo(isbn13);
        }

        @Test
        void shouldSkipABlankIsbn13() {
            final List<IndustryIdentifier> identifiers = List.of(new IndustryIdentifier(ISBN_13, " "));

            final String isbn = GoogleBooksMapper.findIsbn13(identifiers);

            assertThat(isbn).isNull();
        }
    }

    @Test
    @DisplayName("maps the edition's thumbnail to an https cover")
    void mapsThumbnailToHttpsCover() {
        final SourceBook book = mapWith(info -> withImageLinks(info,
            new ImageLinks("http://books.google.com/books/content?id=x&img=1&zoom=1", null)));

        assertThat(book.coverUrl())
            .as("the http thumbnail is upgraded to https so it loads on an https page")
            .isEqualTo("https://books.google.com/books/content?id=x&img=1&zoom=1");
    }

    @Test
    @DisplayName("falls back to the small thumbnail when the full-size one is absent")
    void fallsBackToSmallThumbnail() {
        final String smallThumbnail = "https://books.google.com/books/content?id=x&img=1&zoom=5";

        final SourceBook book = mapWith(info -> withImageLinks(info, new ImageLinks(null, smallThumbnail)));

        assertThat(book.coverUrl()).isEqualTo(smallThumbnail);
    }

    @Test
    @DisplayName("no image links leaves the cover null so another source can supply it")
    void noImageLinksLeavesCoverNull() {
        final SourceBook book = mapWith(info -> info);

        assertThat(book.coverUrl()).isNull();
    }

    @Test
    @DisplayName("turns Google's description tags into spaces and decodes HTML entities")
    void stripsRealGoogleDescriptionMarkup() {
        final String input = "<p><b><i>The Eye of the World</i></b><br>"
            + "Robert Jordan&#39;s &amp; the start of <i>The Wheel of Time</i></p>";
        assertThat(GoogleBooksMapper.stripHtml(input))
            .isEqualTo("The Eye of the World Robert Jordan's & the start of The Wheel of Time");
    }

    @Nested
    @DisplayName("subjects from categories")
    class Subjects {

        @Test
        @DisplayName("no categories leaves subjects null so a refresh does not wipe another source's genres")
        void noCategoriesLeavesSubjectsNull() {
            final SourceBook book = mapWith(info -> withCategories(info, null));

            assertThat(book.rawSubjects())
                .as("null subjects mean 'field absent', distinct from an empty 'no genres'")
                .isNull();
        }
    }

    private SourceBook mapWith(final UnaryOperator<GoogleBooksVolumeInfo> customize) {
        final GoogleBooksVolumeInfo base = new GoogleBooksVolumeInfo(
            TITLE, null, List.of(AUTHOR), "1965", PUBLISHER, 412, LANGUAGE,
            null, null, DESCRIPTION, null);
        final GoogleBooksVolume volume = new GoogleBooksVolume(VOLUME_ID, customize.apply(base));
        return Objects.requireNonNull(mapper.toSourceBook(volume));
    }

    private static GoogleBooksVolumeInfo withCategories(
        final GoogleBooksVolumeInfo info, final @Nullable List<String> categories) {
        return new GoogleBooksVolumeInfo(
            info.title(), info.subtitle(), info.authors(), info.publishedDate(), info.publisher(),
            info.pageCount(), info.language(), info.industryIdentifiers(), categories,
            info.description(), info.imageLinks());
    }

    private static GoogleBooksVolumeInfo withImageLinks(final GoogleBooksVolumeInfo info, final ImageLinks imageLinks) {
        return new GoogleBooksVolumeInfo(
            info.title(), info.subtitle(), info.authors(), info.publishedDate(), info.publisher(),
            info.pageCount(), info.language(), info.industryIdentifiers(), info.categories(),
            info.description(), imageLinks);
    }
}
