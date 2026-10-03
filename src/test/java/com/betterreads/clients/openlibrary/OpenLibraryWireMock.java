package com.betterreads.clients.openlibrary;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.clients.WireMockFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class OpenLibraryWireMock extends WireMockFixture {

    protected static final String SEARCH_PATH = "/search.json";

    protected static void stubSearch(final OpenLibrarySearchJson search) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson(search.toString())));
    }

    protected static void stubWork(final String workKey, final OpenLibraryWorkJson work) {
        WIREMOCK.stubFor(get(urlPathEqualTo(workPath(workKey))).willReturn(okJson(work.toString())));
    }

    protected static void stubWorkStatus(final String workKey, final int status) {
        WIREMOCK.stubFor(get(urlPathEqualTo(workPath(workKey))).willReturn(aResponse().withStatus(status)));
    }

    private static String workPath(final String workKey) {
        return "/works/" + workKey + ".json";
    }

    protected static void stubSearchStatus(final int status) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(status)));
    }

    @DynamicPropertySource
    static void openLibraryProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "openlibrary", "");
        registry.add("openlibrary.contact-email", () -> "user@example.com");
    }
}
