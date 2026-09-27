package com.betterreads.clients.hardcover;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.clients.WireMockFixture;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class HardcoverWebClientConfigTest extends WireMockFixture {

    private static final String PATH = "/graphql";

    private static final String RAW_TOKEN = "hc-secret-token";

    private static final String AUTHORIZATION = "Authorization";

    @Test
    void shouldStripTrailingNewlineFromBearerToken() {
        WIREMOCK.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("{}")));
        final WebClient client = clientWithToken(RAW_TOKEN + "\n");

        client.post().bodyValue("{}").retrieve().bodyToMono(String.class).block();

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(PATH))
            .withHeader(AUTHORIZATION, equalTo("Bearer " + RAW_TOKEN)));
    }

    @Test
    void shouldSendNoAuthorizationHeaderWhenTokenIsBlank() {
        WIREMOCK.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("{}")));
        final WebClient client = clientWithToken(" ");

        client.post().bodyValue("{}").retrieve().bodyToMono(String.class).block();

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo(PATH)).withoutHeader(AUTHORIZATION));
    }

    private WebClient clientWithToken(final String token) {
        final HardcoverProperties properties = new HardcoverProperties(
            baseUrl() + PATH, token, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        return new HardcoverWebClientConfig(properties).hardcoverWebClient();
    }
}
