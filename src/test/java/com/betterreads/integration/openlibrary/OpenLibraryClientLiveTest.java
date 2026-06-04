package com.betterreads.integration.openlibrary;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.openlibrary.client.OpenLibraryClientImpl;
import com.betterreads.integration.openlibrary.mapper.OpenLibraryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Live calls against the real OpenLibrary API for books that stress what OpenLibrary uniquely
 * provides over Google Books: the original publication year and graphic-novel co-creators.
 *
 * <p>Opt-in via {@code RUN_OPENLIBRARY_LIVE=1} so the default build and CI do not depend on a
 * third-party API being reachable. Run locally with
 * {@code RUN_OPENLIBRARY_LIVE=1 ./gradlew test --tests '*OpenLibraryClientLiveTest*'}.
 *
 * <p>Why these books: each one verifies a trust-map claim observed on 2026-05-28.
 * <ul>
 *   <li>Eye of the World (1990), Dune (1965), Hobbit (1937) confirm {@code first_publish_year}
 *       returns the original edition year, the reason OpenLibrary is queried at all. Google's
 *       reprint bias returns 2021, 2016, and a recent reissue for the same titles.</li>
 *   <li>Watchmen confirms OpenLibrary lists the artist co-creators (Gibbons, Higgins) that Google
 *       drops, the second reason OpenLibrary is in the source set.</li>
 * </ul>
 */
@SpringBootTest(
    classes = {
        OpenLibraryWebClientConfig.class,
        OpenLibraryClientImpl.class,
        OpenLibraryMapper.class
    },
    properties = {
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@EnableConfigurationProperties(OpenLibraryProperties.class)
@TestPropertySource(properties = {
    "openlibrary.base-url=https://openlibrary.org",
    "openlibrary.contact-email=test@betterreadsapp.com",
    "openlibrary.connect-timeout=5000",
    "openlibrary.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_OPENLIBRARY_LIVE", matches = "1")
class OpenLibraryClientLiveTest {

    private static final int EYE_OF_THE_WORLD_FIRST_PUBLISHED = 1990;

    private static final int DUNE_FIRST_PUBLISHED = 1965;

    private static final int HOBBIT_FIRST_PUBLISHED = 1937;

    private static final String WATCHMEN_LEAD_AUTHOR = "Alan Moore";

    @Autowired
    private OpenLibraryClientImpl client;

    static Stream<Arguments> originalYearBooks() {
        return Stream.of(
            Arguments.of("The Eye of the World", "Robert Jordan", EYE_OF_THE_WORLD_FIRST_PUBLISHED),
            Arguments.of("Dune", "Frank Herbert", DUNE_FIRST_PUBLISHED),
            Arguments.of("The Hobbit", "J.R.R. Tolkien", HOBBIT_FIRST_PUBLISHED)
        );
    }

    @ParameterizedTest(name = "{0} returns its original publication year {2}, not a reprint year")
    @MethodSource("originalYearBooks")
    @DisplayName("first_publish_year is the original edition year, the OpenLibrary win over Google")
    void returnsOriginalPublicationYear(final String title, final String author, final int originalYear) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .as("OpenLibrary knows this book; an empty result is a real regression")
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.OPEN_LIBRARY);
                assertThat(book.title()).containsIgnoringCase(title);
                assertThat(book.publicationYear())
                    .as("must be the original %d, not a reprint year; this is why OpenLibrary is "
                        + "queried alongside Google Books", originalYear)
                    .isEqualTo(originalYear);
            });
    }

    @ParameterizedTest(name = "{0} returns a clean covers.openlibrary.org cover URL")
    @MethodSource("originalYearBooks")
    @DisplayName("cover URL points at covers.openlibrary.org, the better cover source")
    void returnsResolvableCoverUrl(final String title, final String author, final int ignoredYear) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .isPresent()
            .get()
            .extracting(SourceBook::coverUrl, as(STRING))
            .as("cover must be the canonical OpenLibrary URL, not null and not a Google thumbnail")
            .startsWith("https://covers.openlibrary.org/b/id/")
            .endsWith("-L.jpg");
    }

    @Test
    @DisplayName("Watchmen surfaces the artist co-creators Google omits")
    void watchmenListsCoCreators() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor("Watchmen", WATCHMEN_LEAD_AUTHOR);

        assertThat(result)
            .isPresent()
            .get()
            .extracting(SourceBook::authorNames, as(list(String.class)))
            .as("OpenLibrary lists Gibbons and Higgins; Google returns only Moore. This co-creator "
                + "coverage is the second reason OpenLibrary is in the source set")
            .contains(WATCHMEN_LEAD_AUTHOR, "Dave Gibbons");
    }

    @Test
    @DisplayName("a title that does not exist returns empty, not a fabricated book")
    void unknownTitleReturnsEmpty() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(
            "Zzzzq Nonexistent Title Qzzzz", "No Such Author Xqz");

        assertThat(result)
            .as("OpenLibrary has no such book; the client must return empty, not the closest drift match")
            .isEmpty();
    }
}
