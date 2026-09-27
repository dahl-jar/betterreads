package com.betterreads.sse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class RecordingSseStreams extends SseStreams {

    private @Nullable RecordingEmitter last;

    @Override
    public SseEmitter newEmitter() {
        final RecordingEmitter emitter = new RecordingEmitter();
        last = emitter;
        return emitter;
    }

    public RecordingEmitter lastEmitter() {
        return Objects.requireNonNull(last);
    }

    public static final class RecordingEmitter extends SseEmitter {

        private final List<String> events = new ArrayList<>();

        private boolean closed;

        private boolean sendsFail;

        private @Nullable Runnable completion;

        private @Nullable Runnable timeout;

        private @Nullable Consumer<Throwable> error;

        public List<String> sent() {
            return List.copyOf(events);
        }

        public boolean completed() {
            return closed;
        }

        public void failSends() {
            sendsFail = true;
        }

        public void finish() {
            Objects.requireNonNull(completion).run();
        }

        public void expire() {
            Objects.requireNonNull(timeout).run();
        }

        public void fail() {
            Objects.requireNonNull(error).accept(new IOException("connection reset"));
        }

        @Override
        public void send(final SseEventBuilder event) {
            if (sendsFail) {
                throw new IllegalStateException("emitter already completed");
            }
            events.add(event.build().stream()
                .map(part -> String.valueOf(part.getData()))
                .collect(Collectors.joining()));
        }

        @Override
        public void complete() {
            closed = true;
        }

        @Override
        public void onCompletion(final Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(final Runnable callback) {
            timeout = callback;
        }

        @Override
        public void onError(final Consumer<Throwable> callback) {
            error = callback;
        }
    }
}
