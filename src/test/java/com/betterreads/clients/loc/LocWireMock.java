package com.betterreads.clients.loc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okXml;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.clients.WireMockFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class LocWireMock extends WireMockFixture {

    protected static final String SRU_PATH = "/lcdb";

    protected static void stubSru(final LocRecords response) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(okXml(response.xml())));
    }

    protected static void stubStatus(final int status) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(aResponse().withStatus(status)));
    }

    @DynamicPropertySource
    static void locProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "loc", SRU_PATH);
    }
}
