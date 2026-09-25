package com.betterreads.integration.hardcover;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.integration.WireMockFixture;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class HardcoverWebClientConfigTest extends WireMockFixture {

    private static final String PATH = "/graphql";

    private static final String RAW_TOKEN = "hc-secret-token";

    @Test
    void shouldStripATrailingNewlineFromTheBearerToken() {
        WIREMOCK.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("{}")));
        final HardcoverProperties properties = new HardcoverProperties(
            baseUrl() + PATH, RAW_TOKEN + "\n", CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        final WebClient client = new HardcoverWebClientConfig(properties).hardcoverWebClient();

        client.post().bodyValue("{}").retrieve().bodyToMono(String.class).block();

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(PATH))
            .withHeader("Authorization", equalTo("Bearer " + RAW_TOKEN)));
    }
}
