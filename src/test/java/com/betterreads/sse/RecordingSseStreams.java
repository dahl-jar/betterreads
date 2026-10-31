package com.betterreads.sse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
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

    public static class CallbackEmitter extends SseEmitter {

        private @Nullable Runnable completion;

        private @Nullable Runnable timeout;

        private @Nullable Consumer<Throwable> error;

        public void finish() {
            Objects.requireNonNull(completion).run();
        }

        public void expire() {
            Objects.requireNonNull(timeout).run();
        }

        public void fail() {
            fireError(new IOException("connection reset"));
        }

        protected final void fireError(final Throwable failure) {
            Objects.requireNonNull(error).accept(failure);
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

    public static final class RecordingEmitter extends CallbackEmitter {

        private final List<String> events = new ArrayList<>();

        private final AtomicReference<@Nullable Exception> sendFailure = new AtomicReference<>();

        private final AtomicReference<@Nullable Throwable> closeFailure = new AtomicReference<>();

        private final AtomicBoolean closed = new AtomicBoolean();

        private final AtomicBoolean clientClosed = new AtomicBoolean();

        private final AtomicInteger attempts = new AtomicInteger();

        public List<String> sent() {
            return List.copyOf(events);
        }

        public boolean completed() {
            return closed.get();
        }

        public void failSends() {
            sendFailure.set(new IllegalStateException("emitter already completed"));
        }

        public void failSendsWith(final Exception failure) {
            sendFailure.set(failure);
        }

        public void closeClient() {
            clientClosed.set(true);
            sendFailure.set(new AsyncRequestNotUsableException("Broken pipe"));
        }

        public int sendAttempts() {
            return attempts.get();
        }

        public @Nullable Throwable closedWith() {
            return closeFailure.get();
        }

        @Override
        public void send(final SseEventBuilder event) throws IOException {
            attempts.incrementAndGet();
            final Exception failure = sendFailure.get();
            if (clientClosed.get()) {
                fireError(Objects.requireNonNull(failure));
            }
            if (failure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            if (failure instanceof IllegalStateException stateFailure) {
                throw stateFailure;
            }
            events.add(event.build().stream()
                .map(part -> String.valueOf(part.getData()))
                .collect(Collectors.joining()));
        }

        @Override
        public void complete() {
            closed.set(true);
        }

        @Override
        public void completeWithError(final Throwable failure) {
            closeFailure.set(failure);
        }
    }
}
