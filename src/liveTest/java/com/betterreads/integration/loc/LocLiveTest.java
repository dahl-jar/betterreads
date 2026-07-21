package com.betterreads.integration.loc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceBook;
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

/** Checks catalog lookup and discovery against the live Library of Congress SRU API. */
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
    @DisplayName("slate books carry their LCCNs")
    void slateBooksCarryLccn(final String lccn, final String titleFragment) {
        final Optional<SourceBook> result = client.fetchByLccn(lccn);

        assertThat(result)
            .as("LoC returns lccn %s", lccn)
            .isPresent()
            .get()
            .satisfies(book -> {
                assertThat(book.source()).isEqualTo(BookFieldSource.LOC);
                assertThat(book.locLccn()).isEqualTo(lccn);
                assertThat(book.title()).containsIgnoringCase(titleFragment);
            });
    }

    @Test
    @DisplayName("an unknown LCCN returns empty")
    void unknownLccnReturnsEmpty() {
        assertThat(client.fetchByLccn("0000000000")).isEmpty();
    }

    @Test
    @DisplayName("science-fiction candidates carry identity and catalog date")
    void scienceFictionCandidatesCarryIdentityAndDate() {
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
