package com.betterreads.sse;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.catalina.AccessLog;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

final class ClosedStreamStatusValve extends ValveBase implements AccessLog {

    private static final int CLIENT_CLOSED_STATUS = 499;

    ClosedStreamStatusValve() {
        super(true);
    }

    @Override
    public void invoke(final Request request, final Response response) throws IOException, ServletException {
        getNext().invoke(request, response);
    }

    @Override
    public void log(final Request request, final Response response, final long time) {
        final org.apache.coyote.Response coyote = response.getCoyoteResponse();
        if (isClosedStream(request, coyote)) {
            coyote.setStatus(CLIENT_CLOSED_STATUS);
        }
    }

    @Override
    public void setRequestAttributesEnabled(final boolean enabled) {
    }

    @Override
    public boolean getRequestAttributesEnabled() {
        return false;
    }

    private static boolean isClosedStream(final Request request, final org.apache.coyote.Response coyote) {
        final String contentType = coyote.getContentType();
        return coyote.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR.value()
            && contentType != null
            && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)
            && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof IOException;
    }
}
