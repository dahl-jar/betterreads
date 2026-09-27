package com.betterreads.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequestIdFilter")
class RequestIdFilterTest {

    private static final String MDC_KEY = "requestId";

    private static final String VALID_INCOMING_ID = "abc-123-XYZ";

    private static final int MAX_ID_LENGTH = 64;

    private static final String MAX_LENGTH_ID = "a".repeat(MAX_ID_LENGTH);

    private static final String OVERSIZE_ID = "a".repeat(MAX_ID_LENGTH + 1);

    private static final String ILLEGAL_ID = "bad value with spaces and \r\n";

    private static final String CHAIN_FAILURE_MESSAGE = "downstream blew up";

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        MDC.clear();
    }

    static Stream<Arguments> resolutionCases() {
        final Consumer<AbstractStringAssert<?>> mustBeFreshUuid = id -> id
            .satisfies(UUID::fromString);
        final Consumer<AbstractStringAssert<?>> mustBeIncoming = id -> id.isEqualTo(VALID_INCOMING_ID);
        final Consumer<AbstractStringAssert<?>> mustBeMaxLength = id -> id.isEqualTo(MAX_LENGTH_ID);
        return Stream.of(
            Arguments.of("no header generates a UUID", null, mustBeFreshUuid),
            Arguments.of("valid header is reused as-is", VALID_INCOMING_ID, mustBeIncoming),
            Arguments.of("header at the length limit is reused", MAX_LENGTH_ID, mustBeMaxLength),
            Arguments.of("header with CR/LF is replaced by a UUID", ILLEGAL_ID, mustBeFreshUuid),
            Arguments.of("oversized header is replaced by a UUID", OVERSIZE_ID, mustBeFreshUuid)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolutionCases")
    void resolvedIdFlowsToBothMdcAndResponseHeader(
        final String description,
        final String inboundHeader,
        final Consumer<AbstractStringAssert<?>> resolvedIdInvariant
    ) throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        if (inboundHeader != null) {
            request.addHeader(RequestIdFilter.HEADER, inboundHeader);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seenInChain));

        assertThat(seenInChain.get())
            .as(description)
            .isEqualTo(response.getHeader(RequestIdFilter.HEADER))
            .satisfies(value -> resolvedIdInvariant.accept(assertThat(value)));
    }

    @Test
    void mdcIsClearedAfterChainThrows() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final FilterChain throwingChain = (req, res) -> {
            throw new ServletException(CHAIN_FAILURE_MESSAGE);
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
            .isInstanceOf(ServletException.class)
            .hasMessage(CHAIN_FAILURE_MESSAGE);
        assertThat(MDC.get(MDC_KEY))
            .as("MDC must be cleared even when the downstream chain throws")
            .isNull();
    }

    private static FilterChain capturingChain(final AtomicReference<String> sink) {
        return (req, res) -> sink.set(MDC.get(MDC_KEY));
    }
}
