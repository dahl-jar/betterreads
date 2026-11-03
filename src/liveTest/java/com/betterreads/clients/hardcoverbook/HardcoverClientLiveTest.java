package com.betterreads.clients.hardcoverbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverProperties;
import com.betterreads.clients.hardcover.HardcoverWebClientConfig;
import org.junit.jupiter.api.BeforeEach;
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

    private static final String TOLKIEN = "J.R.R. Tolkien";

    private static final Duration PACE = Duration.ofMillis(1500);

    @Autowired
    private HardcoverClientImpl client;

    @BeforeEach
    void throttle() {
        LockSupport.parkNanos(PACE.toNanos());
    }

    static Stream<Arguments> popularBooks() {
        return Stream.of(
            Arguments.of("A Clash of Kings", "George R.R. Martin"),
            Arguments.of("The Hobbit", TOLKIEN),
            Arguments.of(DUNE, DUNE_AUTHOR),
            Arguments.of("Watchmen", "Alan Moore")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("popularBooks")
    void shouldReturnTheReaderRatingOfAPopularBook(final String title, final String author) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .as("Hardcover returns %s", title)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.HARDCOVER);
                assertThat(book.title()).containsIgnoringCase(title.replace("The ", ""));
                assertThat(book.averageRating())
                    .as("%s has a reader rating", title)
                    .isNotNull()
                    .isGreaterThan(MIN_RATING);
                assertThat(book.ratingCount()).isNotNull().isGreaterThan(MIN_RATING_COUNT);
            });
    }

    @Test
    void shouldKeepTheSeriesOfTheWhiteSandOmnibus() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor("White Sand Omnibus", "Brandon Sanderson");

        assertThat(result).get().satisfies(book -> {
            assertThat(book.hardcoverId()).isEqualTo("1070354");
            assertThat(book.seriesName()).isNotNull();
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
                assertThat(book.seriesPosition()).isEqualTo(1.0);
            });
    }

    @Test
    void shouldGiveTheOneVolumeLordOfTheRingsNoSeries() {
        final Optional<SourceBook> result = client.fetchByTitleAuthor("The Lord of the Rings", TOLKIEN);

        assertThat(result).get().satisfies(book -> {
            assertThat(book.hardcoverId()).isEqualTo("377938");
            assertThat(book.seriesName()).isNull();
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
