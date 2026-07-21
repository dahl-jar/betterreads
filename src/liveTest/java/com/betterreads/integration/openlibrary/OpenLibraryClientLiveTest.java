package com.betterreads.integration.openlibrary;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
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

/** Checks publication years, covers, and co-creators against the live OpenLibrary API. */
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

    @ParameterizedTest(name = "{0} returns original publication year {2}")
    @MethodSource("originalYearBooks")
    @DisplayName("first_publish_year matches the original edition year")
    void returnsOriginalPublicationYear(final String title, final String author, final int originalYear) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .as("OpenLibrary returns the book")
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.OPEN_LIBRARY);
                assertThat(book.title()).containsIgnoringCase(title);
                assertThat(book.publicationYear())
                    .as("publication year matches %d", originalYear)
                    .isEqualTo(originalYear);
            });
    }

    @ParameterizedTest(name = "{0} returns an OpenLibrary cover URL")
    @MethodSource("originalYearBooks")
    @DisplayName("cover URL points at covers.openlibrary.org")
    void returnsCanonicalCoverUrl(final String title, final String author, final int ignoredYear) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .isPresent()
            .get()
            .extracting(SourceBook::coverUrl, as(STRING))
            .as("cover uses the canonical OpenLibrary URL")
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
            .as("author list includes Moore and Gibbons")
            .contains(WATCHMEN_LEAD_AUTHOR, "Dave Gibbons");
    }

    @Test
    @DisplayName("an unknown title returns empty")
    void unknownTitleReturnsEmpty() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(
            "Zzzzq Nonexistent Title Qzzzz", "No Such Author Xqz");

        assertThat(result)
            .as("OpenLibrary returns no record for an unknown title")
            .isEmpty();
    }
}
