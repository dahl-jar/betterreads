package com.betterreads.common.sse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseStreams {

    private static final long TIMEOUT_MILLIS = 300_000L;

    private static final int MAX_OPEN_STREAMS = 500;

    private final Map<String, Set<SseEmitter>> byKey = new ConcurrentHashMap<>();

    private final AtomicInteger openStreams = new AtomicInteger();

    public SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MILLIS);
    }

    public SseEmitter register(final String key) {
        final SseEmitter emitter = newEmitter();
        openStreams.incrementAndGet();
        byKey.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(ignored -> remove(key, emitter));
        emit(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public boolean atCapacity() {
        return openStreams.get() >= MAX_OPEN_STREAMS;
    }

    public Set<SseEmitter> take(final String key) {
        final Set<SseEmitter> streams = byKey.remove(key);
        if (streams == null) {
            return Set.of();
        }
        openStreams.addAndGet(-streams.size());
        return streams;
    }

    public void send(final String key, final String eventName, final Object data) {
        final Set<SseEmitter> streams = byKey.get(key);
        if (streams == null) {
            return;
        }
        streams.forEach(emitter -> emit(emitter, SseEmitter.event().name(eventName).data(data)));
    }

    public void sendAndComplete(final SseEmitter emitter, final String eventName, final Object data) {
        if (emit(emitter, SseEmitter.event().name(eventName).data(data))) {
            try {
                emitter.complete();
            } catch (IllegalStateException ex) {
                emitter.completeWithError(ex);
            }
        }
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

    public void heartbeat() {
        byKey.values().forEach(streams ->
            streams.forEach(emitter -> emit(emitter, SseEmitter.event().comment("keepalive"))));
    }

    public Set<String> openKeys() {
        return Set.copyOf(byKey.keySet());
    }

    public int openCount(final String key) {
        final Set<SseEmitter> streams = byKey.get(key);
        return streams == null ? 0 : streams.size();
    }

    public int openStreamCount() {
        return openStreams.get();
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
    }
}
