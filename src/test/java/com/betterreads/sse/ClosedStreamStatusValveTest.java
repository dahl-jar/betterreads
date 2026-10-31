package com.betterreads.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ClosedStreamStatusValveTest {

    private static final int SERVER_ERROR = HttpStatus.INTERNAL_SERVER_ERROR.value();

    private static final String EVENT_STREAM = MediaType.TEXT_EVENT_STREAM_VALUE;

    private static final Exception CLOSED_CLIENT = new IOException("Broken pipe");

    private final ClosedStreamStatusValve valve = new ClosedStreamStatusValve();

    @Test
    void shouldLogClosedStreamAs499() {
        final org.apache.coyote.Response coyote = coyoteResponse(SERVER_ERROR, EVENT_STREAM);
        final Request request = requestFailedWith(CLOSED_CLIENT);

        valve.log(request, responseWrapping(coyote), 0L);

        assertThat(coyote.getStatus()).isEqualTo(ClosedStreamStatusValve.CLIENT_CLOSED_STATUS);
    }

    @ParameterizedTest
    @MethodSource("otherResponses")
    void shouldKeepStatusWhenResponseIsNotClosedStream(
            final Exception failure, final String contentType, final int status) {
        final org.apache.coyote.Response coyote = coyoteResponse(status, contentType);
        final Request request = requestFailedWith(failure);

        valve.log(request, responseWrapping(coyote), 0L);

        assertThat(coyote.getStatus()).isEqualTo(status);
    }

    static Stream<Arguments> otherResponses() {
        return Stream.of(
            Arguments.of(Named.of("failure from the app", new ServletException("no converter")),
                EVENT_STREAM, SERVER_ERROR),
            Arguments.of(Named.of("json response", CLOSED_CLIENT), MediaType.APPLICATION_JSON_VALUE, SERVER_ERROR),
            Arguments.of(Named.of("status 200", CLOSED_CLIENT), EVENT_STREAM, HttpStatus.OK.value()));
    }

    @Test
    void shouldSupportAsyncRequests() {
        final boolean asyncSupported = valve.isAsyncSupported();

        assertThat(asyncSupported).isTrue();
    }

    private static org.apache.coyote.Response coyoteResponse(final int status, final String contentType) {
        final org.apache.coyote.Response coyote = new org.apache.coyote.Response();
        coyote.setStatus(status);
        coyote.setContentType(contentType);
        return coyote;
    }

    private static Request requestFailedWith(final Exception failure) {
        final Request request = mock(Request.class);
        when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(failure);
        return request;
    }

    private static Response responseWrapping(final org.apache.coyote.Response coyote) {
        final Response response = mock(Response.class);
        when(response.getCoyoteResponse()).thenReturn(coyote);
        return response;
    }
}
