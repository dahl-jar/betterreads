package com.betterreads.integration.googlebooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
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

/**
 * Live calls against the real Google Books REST API for popular books across two categories
 * the catalog must handle correctly: prose novels and graphic novels.
 *
 * <p>Skipped unless {@code GOOGLE_BOOKS_API_KEY} is in the environment. Run locally with
 * {@code source .env && ./gradlew test --tests '*GoogleBooksClientLiveTest*'}.
 *
 * <p>Why these specific books: each one stresses something the mapper has to get right.
 * <ul>
 *   <li>Eye of the World, Clash of Kings, Hobbit, Dune are prose novels with many editions
 *       (graphic-novel adaptations exist for some) so search ranking that picked the wrong
 *       edition or co-authored adaptation would surface as a Robert-Jordan-and-Chuck-Dixon
 *       result on Eye of the World.</li>
 *   <li>Sandman and Watchmen are graphic novels Google labels {@code Comics & Graphic Novels};
 *       if a non-graphic search starts returning these the genre-tag check catches it, and
 *       these confirm the mapper does not assume "single author always means prose."</li>
 *   <li>Clash of Kings stresses the "no ISBN-13" path: Google's top hit is a microfilm scan
 *       with an OTHER-type identifier only. The mapper must leave {@code isbn13} null rather
 *       than synthesize one.</li>
 *   <li>Dune stresses the {@code pageCount=0} path; the mapper must null it out.</li>
 * </ul>
 */
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

    @ParameterizedTest(name = "{0} by {1} comes back as a {2} with clean metadata")
    @MethodSource("popularBooks")
    @DisplayName("popular books across novels and graphic novels return clean, on-genre metadata")
    void popularBookComesBackCleanly(
        final String titleQuery,
        final String authorQuery,
        final Kind kind
    ) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(titleQuery, authorQuery);

        assertThat(result)
            .as("Google Books knows about every book in this slate; an empty result is a real regression")
            .isPresent()
            .get()
            .satisfies(book -> assertCleanMetadata(book, titleQuery, authorQuery, kind));
    }

    private static void assertCleanMetadata(
        final SourceBook book,
        final String titleQuery,
        final String authorQuery,
        final Kind kind
    ) {
        assertThat(book.source()).isEqualTo(BookFieldSource.GOOGLE_BOOKS);
        assertThat(book.title()).containsIgnoringCase(titleQuery);
        assertThat(book.authorNames())
            .as("first listed author must contain the author searched for; "
                + "a different name means we picked an adaptation or different book entirely")
            .isNotNull()
            .first(STRING)
            .containsIgnoringCase(authorQuery);
        if (book.isbn13() != null) {
            assertThat(book.isbn13())
                .as("when ISBN-13 is present it must be a real 978/979 ISBN, never fabricated from ISBN-10")
                .matches("^97[89]\\d{10}$");
        }
        assertThat(book.publicationYear())
            .as("publication year must be a real year, not parsed-wrong from a weird date shape")
            .isNotNull()
            .isBetween(EARLIEST_PLAUSIBLE_YEAR, Year.now(ZoneOffset.UTC).getValue());
        if (book.pageCount() != null) {
            assertThat(book.pageCount())
                .as("Google emits pageCount=0 on some editions; mapper must null those out, "
                    + "and a non-null page count must be a real book length")
                .isGreaterThan(MINIMUM_REAL_PAGE_COUNT);
        }
        if (book.description() != null) {
            assertThat(book.description())
                .as("Google ships <p>/<b>/<i>/<br> in descriptions; the mapper must strip them")
                .doesNotContainPattern("<[^>]+>");
        }
        assertGenreShelf(book, kind);
    }

    private static void assertGenreShelf(final SourceBook book, final Kind kind) {
        if (kind == Kind.GRAPHIC_NOVEL) {
            assertThat(book.rawSubjects())
                .as("graphic novels must reduce to the graphic-novel genre so the catalog shelves them")
                .isNotNull()
                .contains(GRAPHIC_NOVEL_GENRE);
            return;
        }
        assertThat(book.rawSubjects() == null
                || !book.rawSubjects().contains(GRAPHIC_NOVEL_GENRE))
            .as("a prose novel must not be tagged as a graphic novel; "
                + "this catches search drifting to the comic adaptation. "
                + "Got subjects: %s", book.rawSubjects())
            .isTrue();
    }

    private enum Kind {
        NOVEL, GRAPHIC_NOVEL
    }
}
