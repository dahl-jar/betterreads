package com.betterreads.clients.hardcoverbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverProperties;
import com.betterreads.clients.hardcover.HardcoverWebClientConfig;
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

/** Runs the Hardcover client against the live API to catch changes in its response shape. */
@SpringBootTest(
    classes = {
        HardcoverWebClientConfig.class,
        HardcoverClientImpl.class,
        HardcoverMapper.class
    },
    properties = {
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@EnableConfigurationProperties(HardcoverProperties.class)
@TestPropertySource(properties = {
    "hardcover.base-url=https://api.hardcover.app/v1/graphql",
    "hardcover.bearer-token=${HARDCOVER_BEARER_TOKEN:}",
    "hardcover.connect-timeout=5000",
    "hardcover.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "HARDCOVER_BEARER_TOKEN", matches = ".+")
class HardcoverClientLiveTest {

    private static final double MIN_RATING = 3.5;

    private static final int MIN_RATING_COUNT = 500;

    private static final String DUNE = "Dune";

    private static final String DUNE_AUTHOR = "Frank Herbert";

    private static final String SANDMAN = "The Sandman";

    @Autowired
    private HardcoverClientImpl client;

    static Stream<Arguments> slate() {
        return Stream.of(
            Arguments.of("A Clash of Kings", "George R.R. Martin"),
            Arguments.of("The Hobbit", "J.R.R. Tolkien"),
            Arguments.of(DUNE, DUNE_AUTHOR),
            Arguments.of(SANDMAN, "Neil Gaiman"),
            Arguments.of("Watchmen", "Alan Moore")
        );
    }

    static Stream<Arguments> popularBooks() {
        return slate().filter(args -> !SANDMAN.equals(args.get()[0]));
    }

    @ParameterizedTest(name = "{0} resolves to a Hardcover book whose title matches the query")
    @MethodSource("slate")
    @DisplayName("finds each slate book with a matching title")
    void resolvesSlateBook(final String title, final String author) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .as("Hardcover returns %s", title)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.HARDCOVER);
                assertThat(book.title()).containsIgnoringCase(title.replace("The ", ""));
            });
    }

    @ParameterizedTest(name = "{0} returns a real reader rating with a vote count in the thousands")
    @MethodSource("popularBooks")
    @DisplayName("returns a reader rating and rating count for popular books")
    void returnsReaderRating(final String title, final String author) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.averageRating())
                    .as("%s has a reader rating", title)
                    .isNotNull()
                    .isGreaterThan(MIN_RATING);
                assertThat(book.ratingCount()).isNotNull().isGreaterThan(MIN_RATING_COUNT);
            });
    }

    @Test
    @DisplayName("Dune returns its featured series name and position")
    void duneReturnsFeaturedSeries() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(DUNE, DUNE_AUTHOR);

        assertThat(result)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.seriesName()).isEqualTo(DUNE);
                assertThat(book.seriesPosition()).isEqualTo(1);
            });
    }

    @Test
    @DisplayName("an unknown title returns empty")
    void unknownTitleReturnsEmpty() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(
            "Zzzzq Nonexistent Title Qzzzz", "No Such Author Xqz");

        assertThat(result)
            .as("Hardcover returns no record for an unknown title")
            .isEmpty();
    }

    @Test
    @DisplayName("a Hardcover id returns the book description")
    void hardcoverIdReturnsBookDescription() {
        final Optional<SourceBook> result = client.fetchByHardcoverId("427626");

        assertThat(result)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.title()).isEqualTo("The Emperor's Soul");
                assertThat(book.description()).contains("Shai");
            });
    }
}
