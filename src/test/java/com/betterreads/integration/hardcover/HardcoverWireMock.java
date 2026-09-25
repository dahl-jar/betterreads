package com.betterreads.integration.hardcover;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.integration.WireMockFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class HardcoverWireMock extends WireMockFixture {

    protected static final String GRAPHQL_PATH = "/v1/graphql";

    protected static void stubGraphQl(final Object response) {
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).willReturn(okJson(response.toString())));
    }

    protected static void stubGraphQl(final String operation, final Object response) {
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).withRequestBody(containing(operation))
            .willReturn(okJson(response.toString())));
    }

    protected static void stubStatus(final int status) {
        WIREMOCK.stubFor(post(urlPathEqualTo(GRAPHQL_PATH)).willReturn(aResponse().withStatus(status)));
    }

    @DynamicPropertySource
    static void hardcoverProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "hardcover", GRAPHQL_PATH);
        registry.add("hardcover.bearer-token", () -> "test-token");
    }
}
