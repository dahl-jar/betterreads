package com.betterreads.clients.loc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
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
        LocWebClientConfig.class,
        LocSru.class,
        LocClientImpl.class,
        LocMapper.class
    },
    properties = {
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@EnableConfigurationProperties(LocProperties.class)
@TestPropertySource(properties = {
    "loc.base-url=http://lx2.loc.gov:210/lcdb",
    "loc.connect-timeout=5000",
    "loc.read-timeout=15000"
})
@EnabledIfEnvironmentVariable(named = "RUN_LOC_LIVE", matches = "1")
class LocLiveTest {

    @Autowired
    private LocClientImpl client;

    static Stream<Arguments> slate() {
        return Stream.of(
            Arguments.of("Dune", "Frank Herbert"),
            Arguments.of("Watchmen", "Alan Moore"),
            Arguments.of("The Hobbit", "J. R. R. Tolkien"),
            Arguments.of("The Eye of the World", "Robert Jordan"),
            Arguments.of("A Clash of Kings", "George R. R. Martin"));
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("slate")
    void shouldReturnTheCatalogRecordForASlateBook(final String title, final String author) {
        final Optional<SourceBook> result = client.fetchByTitleAuthor(title, author);

        assertThat(result)
            .as("LoC finds %s by %s", title, author)
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
                assertThat(book.locLccn()).isNotBlank();
                assertThat(book.title()).containsIgnoringCase(title);
            });
    }

    @Test
    void shouldReturnEmptyForATitleLocDoesNotHold() {
        final Optional<SourceBook> result =
            client.fetchByTitleAuthor("Qzxv Unheld Title Wyrmwood", "Nobody Realauthor");

        assertThat(result).isEmpty();
    }
}
