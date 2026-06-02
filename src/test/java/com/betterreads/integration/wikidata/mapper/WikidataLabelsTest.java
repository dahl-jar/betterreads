package com.betterreads.integration.wikidata.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Resolves an entity display name from real {@code Special:EntityData} fixtures. */
class WikidataLabelsTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static Stream<Arguments> authorsWithLabels() {
        return Stream.of(
            Arguments.of("Q205739", "Alan Moore"),
            Arguments.of("Q445765", "Dave Gibbons"),
            Arguments.of("Q166351", "Robert Jordan"),
            Arguments.of("Q181677", "George R. R. Martin"),
            Arguments.of("Q892", "J. R. R. Tolkien"),
            Arguments.of("Q210059", "Neil Gaiman"));
    }

    @ParameterizedTest
    @MethodSource("authorsWithLabels")
    void readsTheLabel(final String qid, final String expectedName) {
        assertThat(WikidataLabels.displayName(entity(qid), qid)).isEqualTo(expectedName);
    }

    @Test
    void fallsBackToTheEnwikiTitleWhenTheLabelIsNull() {
        final String herbert = "Q7934";

        assertThat(WikidataLabels.displayName(entity(herbert), herbert)).isEqualTo("Frank Herbert");
    }

    @Test
    void fallsBackToTheQidWhenLabelAndEnwikiAreBothAbsent() {
        final String unknown = "Q99999";

        assertThat(WikidataLabels.displayName(JSON.createObjectNode(), unknown)).isEqualTo(unknown);
    }

    private static JsonNode entity(final String qid) {
        final String json = fixture("author-" + qid + ".json");
        return JSON.readTree(json).path("entities").path(qid);
    }

    private static String fixture(final String name) {
        try {
            return new ClassPathResource("integration/wikidata/" + name)
                .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
