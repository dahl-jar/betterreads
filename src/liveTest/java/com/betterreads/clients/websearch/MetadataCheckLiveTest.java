package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.isbn.IsbnLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = { WebSearchRunner.class, MetadataCheckClientImpl.class },
    properties = {
        "spring.main.web-application-type=none",
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    }
)
@EnableConfigurationProperties(WebSearchProperties.class)
@TestPropertySource(properties = "web-search.hook-script=docker/websearch/restrict-search.mjs")
@EnabledIfEnvironmentVariable(named = "RUN_WEB_SEARCH_LIVE", matches = "true")
class MetadataCheckLiveTest {

    private static final long WITCHWOOD = 1L;

    private static final int YEAR = 2017;

    private static final String GERMAN_ISBN = "9783608949650";

    @Autowired
    private MetadataCheckClient client;

    @Test
    void shouldCorrectForeignEdition() {
        final MetadataCheckRequest witchwood = new MetadataCheckRequest(WITCHWOOD, "Die Hexenholzkrone",
            List.of("Tad Williams"), YEAR, "Der letzte König von Osten Ard", 1.0, GERMAN_ISBN, null);

        final Optional<Map<Long, VerifiedMetadata>> checks = client.check(List.of(witchwood));

        assertThat(checks).get().satisfies(map -> {
            final VerifiedMetadata metadata = map.get(WITCHWOOD);
            assertThat(metadata.title()).isEqualTo("The Witchwood Crown");
            assertThat(metadata.seriesName()).endsWith("Last King of Osten Ard");
            assertThat(metadata.seriesPosition()).isEqualTo(1.0);
            assertThat(IsbnLanguage.languageOf(metadata.isbn13())).isEqualTo("en");
        });
    }
}
