package com.betterreads.integration.openlibrary;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.integration.WireMockFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class OpenLibraryWireMock extends WireMockFixture {

    protected static final String SEARCH_PATH = "/search.json";

    protected static void stubSearch(final OpenLibrarySearchJson search) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(search.toString())));
    }

    protected static void stubWork(final String workKey, final OpenLibraryWorkJson work) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/works/" + workKey + ".json")).willReturn(okJson(work.toString())));
    }

    protected static void stubSearchStatus(final int status) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(status)));
    }

    @DynamicPropertySource
    static void openLibraryProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "openlibrary", "");
        registry.add("openlibrary.contact-email", () -> "darrow@example.com");
    }
}
