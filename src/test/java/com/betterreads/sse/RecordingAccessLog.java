package com.betterreads.sse;

import java.io.CharArrayWriter;
import java.util.concurrent.CompletableFuture;
import org.apache.catalina.valves.AbstractAccessLogValve;

public final class RecordingAccessLog extends AbstractAccessLogValve {

    private static final String STATUS_PATTERN = "%s";

    private final CompletableFuture<String> status = new CompletableFuture<>();

    public RecordingAccessLog() {
        super();
        setPattern(STATUS_PATTERN);
    }

    public CompletableFuture<String> loggedStatus() {
        return status.copy();
    }

    @Override
    protected void log(final CharArrayWriter message) {
        status.complete(message.toString());
    }
}
