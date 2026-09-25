package com.betterreads.integration.loc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okXml;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.betterreads.integration.WireMockFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class LocWireMock extends WireMockFixture {

    protected static final String SRU_PATH = "/lcdb";

    protected static void stubSru(final LocRecords response) {
        stubSru(response.xml());
    }

    protected static void stubSru(final LocMarcRecords response) {
        stubSru(response.xml());
    }

    private static void stubSru(final String xml) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(okXml(xml)));
    }

    protected static void stubSruPage(final int startRecord, final LocMarcRecords response) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH))
            .withQueryParam("startRecord", equalTo(Integer.toString(startRecord)))
            .willReturn(okXml(response.xml())));
    }

    protected static void stubStatus(final int status) {
        WIREMOCK.stubFor(get(urlPathEqualTo(SRU_PATH)).willReturn(aResponse().withStatus(status)));
    }

    @DynamicPropertySource
    static void locProperties(final DynamicPropertyRegistry registry) {
        registerSource(registry, "loc", SRU_PATH);
    }
}
