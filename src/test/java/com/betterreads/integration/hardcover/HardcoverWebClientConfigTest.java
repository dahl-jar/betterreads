package com.betterreads.integration.hardcover;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The Hardcover bearer token reaches the Authorization header without surrounding whitespace, so a
 * secret sealed with a trailing newline does not produce a header value Netty rejects.
 */
class HardcoverWebClientConfigTest {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    private static final int READ_TIMEOUT_MS = 5000;

    private static final String PATH = "/graphql";

    private static final String RAW_TOKEN = "hc-secret-token";

    private WireMockServer wireMock;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        wireMock.stubFor(post(urlPathEqualTo(PATH))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{}")));
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    @DisplayName("a token sealed with a trailing newline is sent as a clean Bearer header")
    void stripsWhitespaceFromBearerToken() {
        final HardcoverProperties properties = new HardcoverProperties(
            "http://localhost:" + wireMock.port() + PATH, RAW_TOKEN + "\n",
            CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        final WebClient client = new HardcoverWebClientConfig(properties).hardcoverWebClient();

        client.post().bodyValue("{}").retrieve().bodyToMono(String.class).block();

        wireMock.verify(postRequestedFor(urlPathEqualTo(PATH))
            .withHeader("Authorization", equalTo("Bearer " + RAW_TOKEN)));
    }
}
