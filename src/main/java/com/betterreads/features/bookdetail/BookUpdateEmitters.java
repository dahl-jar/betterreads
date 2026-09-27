package com.betterreads.features.bookdetail;

import com.betterreads.sse.HeartbeatStreams;
import com.betterreads.sse.SseStreams;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open detail-page SSE streams, keyed by book key.
 *
 * <p>An incomplete book is filled in once, so one {@code book-updated} event goes to every stream on
 * the key and completes it.
 */
@Component
class BookUpdateEmitters implements HeartbeatStreams {

    private static final String EVENT_NAME = "book-updated";

    private final SseStreams streams;

    BookUpdateEmitters() {
        this(new SseStreams());
    }

    BookUpdateEmitters(final SseStreams streams) {
        this.streams = streams;
    }

    /**
     * A complete book is sent at once. An incomplete book holds the stream open until its update arrives.
     *
     * <p>A promotion can commit between the first read and registering, so the book is re-read after
     * registering and sent at once if it is complete by then.
     */
    public SseEmitter open(
        final String key,
        final BookDetailResponse current,
        final Supplier<Optional<BookDetailResponse>> reread
    ) {
        if (current.complete() || streams.atCapacity()) {
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

    @Override
    public void heartbeat() {
        streams.heartbeat();
    }

    private void send(final SseEmitter emitter, final BookDetailResponse detail) {
        streams.sendAndComplete(emitter, EVENT_NAME, detail);
    }
}
