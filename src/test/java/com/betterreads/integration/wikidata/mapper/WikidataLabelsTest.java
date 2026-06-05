package com.betterreads.integration.wikidata.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Resolves an entity display name from its label, enwiki title, or QID. */
class WikidataLabelsTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void readsTheEnglishLabel() {
        final String name = "Alan Moore";
        final ObjectNode entity = JSON.createObjectNode();
        entity.putObject("labels").putObject("en").put("value", name);

        assertThat(WikidataLabels.displayName(entity, "Q205739")).isEqualTo(name);
    }

    @Test
    void fallsBackToTheEnwikiTitleWhenTheLabelIsNull() {
        final String name = "Frank Herbert";
        final ObjectNode entity = JSON.createObjectNode();
        entity.putObject("sitelinks").putObject("enwiki").put("title", name);

        assertThat(WikidataLabels.displayName(entity, "Q7934")).isEqualTo(name);
    }

    @Test
    void fallsBackToTheQidWhenLabelAndEnwikiAreBothAbsent() {
        final String unknown = "Q99999";

        assertThat(WikidataLabels.displayName(JSON.createObjectNode(), unknown)).isEqualTo(unknown);
    }
}
