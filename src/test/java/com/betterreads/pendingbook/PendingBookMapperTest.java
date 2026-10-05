package com.betterreads.pendingbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.betterreads.booksource.BookField;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CreditRole;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.junit.jupiter.api.Test;

class PendingBookMapperTest {

    private static final String ISBN = "9780345539786";

    private static final double RATING = 4.27;

    private static final int RATING_COUNT = 812;

    private static final int YEAR = 2014;

    private static final int PAGES = 382;

    private static final double FIRST_VOLUME = 1;

    private static final String TABBED_NAME = "Patrick\tRothfuss";

    private static final SourceBook ORIGINAL = SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
        .isbn13(ISBN)
        .openLibraryWorkKey("OL1W")
        .googleBooksVolumeId("gb1")
        .hardcoverId("hc1")
        .locLccn("2013033409")
        .wikidataQid("Q1")
        .title("Red Rising")
        .subtitle("Book One")
        .description("Darrow is a Red, a miner beneath the surface of Mars.")
        .coverUrl("https://covers.example.test/red-rising.jpg")
        .publicationYear(YEAR)
        .pageCount(PAGES)
        .language("en")
        .publisher("Del Rey")
        .averageRating(RATING)
        .ratingCount(RATING_COUNT)
        .seriesName("Red Rising Saga")
        .seriesPosition(FIRST_VOLUME)
        .rawSubjects(List.of("Science Fiction", "Dystopia"))
        .awards(List.of("Goodreads Choice"))
        .authors(List.of(SourceAuthor.ofName("Author"), SourceAuthor.ofName("Other Author")))
        .build();

    private static final MergedBook MERGED = new MergedBook(ORIGINAL,
        Map.of(
            BookField.TITLE, BookFieldSource.GOOGLE_BOOKS,
            BookField.DESCRIPTION, BookFieldSource.HARDCOVER,
            BookField.COVER, BookFieldSource.OPEN_LIBRARY,
            BookField.PUBLICATION_YEAR, BookFieldSource.LOC),
        Set.of(BookFieldSource.WIKIDATA), Set.of());

    private final PendingBookMapper mapper = new PendingBookMapper();

    @Test
    void shouldReadBackEveryFieldStoredInTheRow() {
        final PendingBook row = new PendingBook();
        mapper.applyTo(row, MERGED);

        final SourceBook readBack = mapper.toSourceBook(row);

        assertThat(readBack).usingRecursiveComparison().ignoringFields("source").isEqualTo(ORIGINAL);
        assertThat(readBack.source()).isEqualTo(BookFieldSource.STAGED);
    }

    @Test
    void shouldStoreFieldProvenanceInTheRow() {
        final PendingBook row = new PendingBook();

        mapper.applyTo(row, MERGED);

        assertThat(row).satisfies(stored -> {
            assertThat(stored.getDedupKey()).isEqualTo(ISBN);
            assertThat(stored.getTitleSource()).isEqualTo("GOOGLE_BOOKS");
            assertThat(stored.getDescriptionSource()).isEqualTo("HARDCOVER");
            assertThat(stored.getCoverSource()).isEqualTo("OPEN_LIBRARY");
            assertThat(stored.getPublicationYearSource()).isEqualTo("LOC");
            assertThat(stored.getSubjectsSources()).isEqualTo("WIKIDATA");
        });
    }

    @Test
    void shouldRoundTripRoles() {
        final List<SourceAuthor> credits = List.of(
            SourceAuthor.ofName("Patrick Rothfuss"), SourceAuthor.withRole("Marc Simonetti", CreditRole.ILLUSTRATOR));
        final PendingBook row = stored(credits);

        final SourceBook readBack = mapper.toSourceBook(row);

        assertThat(readBack.authors()).isEqualTo(credits);
    }

    @Test
    void shouldReadRolelessLinesAsAuthor() {
        final PendingBook row = new PendingBook();
        row.setAuthors("Patrick Rothfuss\nMarc Simonetti");

        final SourceBook readBack = mapper.toSourceBook(row);

        assertThat(readBack.authors())
            .extracting(SourceAuthor::role)
            .containsExactly(CreditRole.AUTHOR, CreditRole.AUTHOR);
    }

    @Test
    void shouldKeepOneCreditForANameWithTabAndNewline() {
        final SourceAuthor illustrator = SourceAuthor.withRole("Marc\tSimonetti\nCover", CreditRole.ILLUSTRATOR);
        final PendingBook row = stored(List.of(illustrator));

        final SourceBook readBack = mapper.toSourceBook(row);

        assertThat(readBack.authors())
            .containsExactly(SourceAuthor.withRole("Marc Simonetti Cover", CreditRole.ILLUSTRATOR));
    }

    @Test
    void shouldReadUnknownRoleSuffixAsAuthorName() {
        final PendingBook row = new PendingBook();
        row.setAuthors(TABBED_NAME);

        final SourceBook readBack = mapper.toSourceBook(row);

        assertThat(readBack.authors()).containsExactly(SourceAuthor.ofName(TABBED_NAME));
    }

    @Test
    void shouldReadBackEmptyListColumnsAsMissing() {
        final PendingBook row = new PendingBook();
        row.setSubjects("");
        row.setAwards("");
        row.setAuthors("");

        final SourceBook readBack = mapper.toSourceBook(row);

        assertThat(readBack).satisfies(book -> {
            assertThat(book.rawSubjects()).isNull();
            assertThat(book.awards()).isNull();
            assertThat(book.authors()).isNull();
        });
    }

    private PendingBook stored(final List<SourceAuthor> credits) {
        final PendingBook row = new PendingBook();
        final SourceBook book = ORIGINAL.toBuilder().authors(credits).build();
        mapper.applyTo(row, new MergedBook(book, Map.of(), Set.of(), Set.of()));
        return row;
    }
}
