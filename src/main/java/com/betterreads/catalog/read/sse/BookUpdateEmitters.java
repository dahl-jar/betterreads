package com.betterreads.catalog.read.sse;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.common.util.LogSanitizer;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds the open detail-page SSE streams, keyed by book key, and pushes the filled-in book to them.
 *
 * <p>The detail page opens a stream while a cold book finishes enriching. When the book is written,
 * {@link #publish} sends one {@code book-updated} event to every stream watching that key and
 * completes them, since the fill is a one-time event. An emitter removes itself on completion,
 * timeout, or error.
 */
@Component
public class BookUpdateEmitters {

    private static final Logger LOG = LoggerFactory.getLogger(BookUpdateEmitters.class);

    private static final String EVENT_NAME = "book-updated";

    private static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;

    private static final int MAX_OPEN_STREAMS = 500;

    private final Map<String, Set<SseEmitter>> byKey = new ConcurrentHashMap<>();

    private final AtomicInteger openStreams = new AtomicInteger();

    /** Registers a new stream for the key and removes it again when it ends. */
    public SseEmitter register(final String key, final long timeoutMillis) {
        final SseEmitter emitter = new SseEmitter(timeoutMillis);
        openStreams.incrementAndGet();
        byKey.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(ignored -> remove(key, emitter));
        return emitter;
    }

    /** Registers a stream with the default timeout. */
    public SseEmitter register(final String key) {
        return register(key, DEFAULT_TIMEOUT_MILLIS);
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
        if (complete || openStreams.get() >= MAX_OPEN_STREAMS) {
            final SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
            send(emitter, current);
            return emitter;
        }
        final SseEmitter emitter = register(key);
        connectComment(emitter);
        reread.get()
            .filter(BookDetailResponse::complete)
            .ifPresent(detail -> publish(key, detail));
        return emitter;
    }

    /**
     * Sends the filled-in book to every stream on the key, then completes them.
     *
     * <p>The key's set is taken out of the registry first, so each emitter's completion callback finds
     * the key already gone and does not adjust the count again; the count is decremented here per
     * stream instead.
     */
    public void publish(final String key, final BookDetailResponse detail) {
        final Set<SseEmitter> streams = byKey.remove(key);
        if (streams == null) {
            return;
        }
        for (final SseEmitter emitter : streams) {
            openStreams.decrementAndGet();
            send(emitter, detail);
        }
    }

    /** Returns the number of open streams on the key. */
    public int openCount(final String key) {
        final Set<SseEmitter> streams = byKey.get(key);
        return streams == null ? 0 : streams.size();
    }

    /** Returns the total open streams across every key, the value the cap is checked against. */
    public int openStreamCount() {
        return openStreams.get();
    }

    /** Sends a keepalive comment to every open stream so an idle connection is not cut. */
    public void heartbeat() {
        byKey.values().forEach(streams -> streams.forEach(this::keepAlive));
    }

    private void send(final SseEmitter emitter, final BookDetailResponse detail) {
        if (emit(emitter, SseEmitter.event().name(EVENT_NAME).data(detail))) {
            try {
                emitter.complete();
            } catch (IllegalStateException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private void keepAlive(final SseEmitter emitter) {
        emit(emitter, SseEmitter.event().comment("keepalive"));
    }

    private void connectComment(final SseEmitter emitter) {
        emit(emitter, SseEmitter.event().comment("connected"));
    }

    private boolean emit(final SseEmitter emitter, final SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
            return false;
        }
    }

    private void remove(final String key, final SseEmitter emitter) {
        final boolean[] removed = {false};
        byKey.computeIfPresent(key, (ignored, streams) -> {
            removed[0] = streams.remove(emitter);
            return streams.isEmpty() ? null : streams;
        });
        if (removed[0]) {
            openStreams.decrementAndGet();
        }
        LOG.debug("catalog.sse stream closed key={}", LogSanitizer.forLog(key));
    }
}
