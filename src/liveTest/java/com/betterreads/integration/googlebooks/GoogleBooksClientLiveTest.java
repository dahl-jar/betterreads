package com.betterreads.integration.googlebooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.googlebooks.client.GoogleBooksClientImpl;
import com.betterreads.integration.googlebooks.mapper.GoogleBooksMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Checks prose and graphic-novel metadata against the live Google Books API. */
@SpringBootTest(
    classes = { GoogleBooksWebClientConfig.class, GoogleBooksClientImpl.class, GoogleBooksMapper.class },
    properties = {
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@EnableConfigurationProperties(GoogleBooksProperties.class)
@TestPropertySource(properties = {
    "googlebooks.base-url=https://www.googleapis.com/books/v1",
    "googlebooks.connect-timeout=5000",
    "googlebooks.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "GOOGLE_BOOKS_API_KEY", matches = ".+")
class GoogleBooksClientLiveTest {

    private static final String GRAPHIC_NOVEL_GENRE = "graphic novel";

    private static final int EARLIEST_PLAUSIBLE_YEAR = 1900;

    private static final int MINIMUM_REAL_PAGE_COUNT = 50;

    @Autowired
    private GoogleBooksClientImpl client;

    static Stream<Arguments> popularBooks() {
        return Stream.of(
            Arguments.of("The Eye of the World", "Robert Jordan", Kind.NOVEL),
            Arguments.of("A Clash of Kings", "George R. R. Martin", Kind.NOVEL),
            Arguments.of("The Hobbit", "Tolkien", Kind.NOVEL),
            Arguments.of("Dune", "Frank Herbert", Kind.NOVEL),
            Arguments.of("The Sandman", "Neil Gaiman", Kind.GRAPHIC_NOVEL),
            Arguments.of("Watchmen", "Alan Moore", Kind.GRAPHIC_NOVEL)
        );
    }

    @ParameterizedTest(name = "{0} by {1} returns expected metadata for {2}")
    @MethodSource("popularBooks")
    @DisplayName("popular prose and graphic novels return expected metadata")
    void popularBooksCarryExpectedMetadata(
        final String titleQuery,
        final String authorQuery,
        final Kind kind
    ) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(titleQuery, authorQuery);

        assertThat(result)
            .as("Google Books returns the book")
            .isPresent()
            .get()
            .satisfies(book -> assertExpectedMetadata(book, titleQuery, authorQuery, kind));
    }

    private static void assertExpectedMetadata(
        final SourceBook book,
        final String titleQuery,
        final String authorQuery,
        final Kind kind
    ) {
        assertThat(book.source()).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        assertThat(book.title()).containsIgnoringCase(titleQuery);
        assertThat(book.authorNames())
            .as("first listed author matches the search author")
            .isNotNull()
            .first(STRING)
            .containsIgnoringCase(authorQuery);
        if (book.isbn13() != null) {
            assertThat(book.isbn13())
                .as("present ISBN-13 starts with 978 or 979")
                .matches("^97[89]\\d{10}$");
        }
        assertThat(book.publicationYear())
            .as("publication year falls within the catalog range")
            .isNotNull()
            .isBetween(EARLIEST_PLAUSIBLE_YEAR, Year.now(ZoneOffset.UTC).getValue());
        if (book.pageCount() != null) {
            assertThat(book.pageCount())
                .as("present page count exceeds the metadata floor")
                .isGreaterThan(MINIMUM_REAL_PAGE_COUNT);
        }
        if (book.description() != null) {
            assertThat(book.description())
                .as("description contains no HTML tags")
                .doesNotContainPattern("<[^>]+>");
        }
        assertExpectedGenre(book, kind);
    }

    private static void assertExpectedGenre(final SourceBook book, final Kind kind) {
        if (kind == Kind.GRAPHIC_NOVEL) {
            assertThat(book.rawSubjects())
                .as("graphic novels carry the graphic-novel genre")
                .isNotNull()
                .contains(GRAPHIC_NOVEL_GENRE);
            return;
        }
        assertThat(book.rawSubjects() == null
                || !book.rawSubjects().contains(GRAPHIC_NOVEL_GENRE))
            .as("subjects classify the title as prose: %s", book.rawSubjects())
            .isTrue();
    }

    private enum Kind {
        NOVEL, GRAPHIC_NOVEL
    }
}
