package com.betterreads.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;

public class WireMockFixture {

    protected static final WireMockServer WIREMOCK = startServer();

    protected static final int CONNECT_TIMEOUT_MS = 2000;

    protected static final int READ_TIMEOUT_MS = 5000;

    protected static final int HTTP_UNAUTHORIZED = 401;

    protected static final int HTTP_NOT_FOUND = 404;

    protected static final int HTTP_SERVER_ERROR = 503;

    private static WireMockServer startServer() {
        final WireMockServer server = new WireMockServer(0);
        server.start();
        return server;
    }

    protected static String baseUrl() {
        return "http://localhost:" + WIREMOCK.port();
    }

    protected static void registerSource(
        final DynamicPropertyRegistry registry, final String prefix, final String path) {
        registry.add(prefix + ".base-url", () -> baseUrl() + path);
        registry.add(prefix + ".connect-timeout", () -> CONNECT_TIMEOUT_MS);
        registry.add(prefix + ".read-timeout", () -> READ_TIMEOUT_MS);
    }

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }
}
