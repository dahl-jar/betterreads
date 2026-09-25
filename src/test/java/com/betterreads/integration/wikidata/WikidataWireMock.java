package com.betterreads.integration.wikidata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.integration.WireMockFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class WikidataWireMock extends WireMockFixture {

    protected static final String SEARCH_PATH = "/w/api.php";

    private static String entityPath(final String qid) {
        return "/wiki/Special:EntityData/" + qid + ".json";
    }

    protected static void stubEntity(final WikidataEntityJson entity) {
        WIREMOCK.stubFor(get(urlPathEqualTo(entityPath(entity.qid()))).willReturn(okJson(entity.entityData())));
    }

    protected static void stubEntityStatus(final String qid, final int status) {
        WIREMOCK.stubFor(get(urlPathEqualTo(entityPath(qid))).willReturn(aResponse().withStatus(status)));
    }

    protected static void stubSearch(final WikidataSearchJson search) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(search.toString())));
    }

    @DynamicPropertySource
    static void wikidataProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "wikidata", "");
    }
}
