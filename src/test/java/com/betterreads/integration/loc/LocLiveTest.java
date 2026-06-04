package com.betterreads.integration.loc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.loc.client.LocClientImpl;
import com.betterreads.integration.loc.discovery.LocDiscoveryClientImpl;
import com.betterreads.integration.loc.discovery.LocDiscoveryRecord;
import com.betterreads.integration.loc.mapper.LocMapper;
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
 * Live calls against the real LoC SRU endpoint on {@code lx2.loc.gov:210}.
 *
 * <p>Opt-in via {@code RUN_LOC_LIVE=1} so the default build and CI never depend on the endpoint
 * being reachable or on the outbound tcp/210 egress being open. Run with
 * {@code RUN_LOC_LIVE=1 ./gradlew test --tests '*LocLiveTest*'}.
 */
@SpringBootTest(
    classes = {
        LocWebClientConfig.class,
        LocSru.class,
        LocClientImpl.class,
        LocDiscoveryClientImpl.class,
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

    private static final String SCI_FI = "science fiction";

    private static final int DISCOVERY_YEAR = 2024;
    private static final LocalDate DISCOVERY_CUTOFF = LocalDate.of(2024, 1, 1);

    @Autowired
    private LocClientImpl client;

    @Autowired
    private LocDiscoveryClientImpl discoveryClient;

    static Stream<Arguments> slate() {
        return Stream.of(
            Arguments.of("2019287107", "Dune"),
            Arguments.of("2013003992", "Watchmen"),
            Arguments.of("2024442463", "hobbit"),
            Arguments.of("92159876", "Sandman"),
            Arguments.of("89007939", "eye of the world"),
            Arguments.of("98037954", "clash of kings"));
    }

    @ParameterizedTest(name = "{1} (lccn {0})")
    @MethodSource("slate")
    @DisplayName("fetchByLccn resolves the slate book and stamps its LCCN")
    void fetchByLccnResolvesSlate(final String lccn, final String titleFragment) {
        final Optional<SourceBook> result = client.fetchByLccn(lccn);

        assertThat(result)
            .as("an empty result for lccn %s is a regression", lccn)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
                assertThat(book.locLccn()).isEqualTo(lccn);
                assertThat(book.title()).containsIgnoringCase(titleFragment);
            });
    }

    @Test
    @DisplayName("an unknown LCCN returns empty, not a fabricated record")
    void unknownLccnReturnsEmpty() {
        assertThat(client.fetchByLccn("0000000000")).isEmpty();
    }

    @Test
    @DisplayName("discoverByDate returns dated science-fiction candidates with ISBN and title")
    void discoverByDateReturnsDatedCandidates() {
        final List<LocDiscoveryRecord> found =
            discoveryClient.discoverByDate(DISCOVERY_YEAR, SCI_FI, DISCOVERY_CUTOFF);

        assertThat(found)
            .as("the 2024 science-fiction bucket has cataloged records")
            .isNotEmpty()
            .allSatisfy(record -> {
                assertThat(record.lccn()).isNotBlank();
                assertThat(record.title()).isNotBlank();
                assertThat(record.catalogedOn()).isAfterOrEqualTo(DISCOVERY_CUTOFF);
            });
    }
}
