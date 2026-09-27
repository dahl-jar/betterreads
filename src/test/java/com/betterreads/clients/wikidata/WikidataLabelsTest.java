package com.betterreads.clients.wikidata;

import static com.betterreads.clients.wikidata.WikidataEntityJson.HERBERT_NAME;
import static com.betterreads.clients.wikidata.WikidataEntityJson.HERBERT_QID;
import static com.betterreads.clients.wikidata.WikidataEntityJson.MOORE_NAME;
import static com.betterreads.clients.wikidata.WikidataEntityJson.MOORE_QID;
import static com.betterreads.clients.wikidata.WikidataEntityJson.entity;
import static com.betterreads.clients.wikidata.WikidataEntityJson.herbert;
import static com.betterreads.clients.wikidata.WikidataEntityJson.moore;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class WikidataLabelsTest {

    @Test
    void readsTheEnglishLabel() {
        final ObjectNode entity = moore().node();

        assertThat(WikidataLabels.displayName(entity, MOORE_QID)).isEqualTo(MOORE_NAME);
    }

    @Test
    void shouldFallBackToTheEnwikiTitleWhenTheEnglishLabelIsMissing() {
        final ObjectNode entity = herbert().node();

        assertThat(WikidataLabels.displayName(entity, HERBERT_QID)).isEqualTo(HERBERT_NAME);
    }

    @Test
    void fallsBackToTheQidWhenLabelAndEnwikiAreBothAbsent() {
        final String unknown = "Q99999";
        final ObjectNode entity = entity().withoutLabels().withoutSitelinks().node();

        assertThat(WikidataLabels.displayName(entity, unknown)).isEqualTo(unknown);
    }
}
