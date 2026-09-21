package com.betterreads.catalog.read.sse;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.common.sse.HeartbeatStreams;
import com.betterreads.common.sse.SseStreams;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds the open detail-page SSE streams, keyed by book key, and pushes the filled-in book to them.
 *
 * <p>The detail page opens a stream while an incomplete book is being filled in. When the book is
 * written, {@link #publish} sends one {@code book-updated} event to every stream watching that key
 * and completes them, since the fill is a one-time event. An emitter removes itself on completion,
 * timeout, or error.
 */
@Component
public class BookUpdateEmitters implements HeartbeatStreams {

    private static final String EVENT_NAME = "book-updated";

    private final SseStreams streams = new SseStreams();

    /** Registers a new stream for the key and removes it again when it ends. */
    public SseEmitter register(final String key) {
        return streams.register(key);
    }

    /**
     * Opens a detail-page stream for the key. A complete book is sent at once; an incomplete book
     * holds the stream open until its update arrives.
     *
     * <p>The stream is registered before the book is re-read so a promotion that commits during the
     * open never slips between the read and the registration: if the re-read then shows the book
     * complete, it is sent at once on the registered stream, closing the miss window.
     *
     * @param reread resolves the book's current detail, used to recheck completeness after registering
     */
    public SseEmitter open(
        final String key,
        final BookDetailResponse current,
        final boolean complete,
        final Supplier<Optional<BookDetailResponse>> reread
    ) {
        if (complete || streams.atCapacity()) {
            final SseEmitter emitter = streams.newEmitter();
            send(emitter, current);
            return emitter;
        }
        final SseEmitter emitter = streams.register(key);
        reread.get()
            .filter(BookDetailResponse::complete)
            .ifPresent(detail -> publish(key, detail));
        return emitter;
    }

    /** Sends the filled-in book to every stream on the key, then completes them. */
    public void publish(final String key, final BookDetailResponse detail) {
        streams.take(key).forEach(emitter -> send(emitter, detail));
    }

    /** Returns the number of open streams on the key. */
    public int openCount(final String key) {
        return streams.openCount(key);
    }

    /** Returns the total open streams across every key, the value the cap is checked against. */
    public int openStreamCount() {
        return streams.openStreamCount();
    }

    @Override
    public void heartbeat() {
        streams.heartbeat();
    }

    private void send(final SseEmitter emitter, final BookDetailResponse detail) {
        streams.sendAndComplete(emitter, EVENT_NAME, detail);
    }
}
