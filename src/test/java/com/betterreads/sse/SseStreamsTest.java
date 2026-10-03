package com.betterreads.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.betterreads.sse.RecordingSseStreams.RecordingEmitter;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

class SseStreamsTest {

    private static final String KEY = "book-one";

    private static final String OTHER_KEY = "book-two";

    private static final int STREAM_CAP = 500;

    private static final String EVENT = "book-updated";

    private static final String DATA = "Dune";

    private static final String KEEPALIVE = "keepalive";

    private final RecordingSseStreams streams = new RecordingSseStreams();

    @ParameterizedTest
    @MethodSource("disconnects")
    void shouldDropKeyWhenStreamEnds(final Consumer<RecordingEmitter> disconnect) {
        streams.register(KEY);
        final RecordingEmitter emitter = streams.lastEmitter();

        disconnect.accept(emitter);

        assertThat(streams.openKeys()).doesNotContain(KEY);
    }

    static Stream<Named<Consumer<RecordingEmitter>>> disconnects() {
        return Stream.of(
            Named.of("client closes", RecordingEmitter::finish),
            Named.of("timeout", RecordingEmitter::expire),
            Named.of("network error", RecordingEmitter::fail));
    }

    @Test
    void shouldFreeSlotWhenStreamEnds() {
        IntStream.range(0, STREAM_CAP).forEach(ignored -> streams.register(KEY));
        final RecordingEmitter emitter = streams.lastEmitter();

        emitter.finish();

        assertThat(streams.atCapacity()).isFalse();
    }

    @Test
    void shouldBeAtCapacityWhenCapIsReached() {
        IntStream.range(0, STREAM_CAP).forEach(ignored -> streams.register(KEY));

        final boolean atCapacity = streams.atCapacity();

        assertThat(atCapacity).isTrue();
    }

    @Test
    void shouldKeepSlotCountWhenTakenStreamEnds() {
        streams.register(KEY);
        final RecordingEmitter taken = streams.lastEmitter();
        streams.take(KEY);
        taken.finish();

        IntStream.range(0, STREAM_CAP).forEach(ignored -> streams.register(KEY));

        assertThat(streams.atCapacity()).isTrue();
    }

    @Test
    void shouldSendKeepaliveToOpenStreams() {
        streams.register(KEY);
        final RecordingEmitter emitter = streams.lastEmitter();

        streams.heartbeat();

        assertThat(emitter.sent()).last().asString().contains(KEEPALIVE);
    }

    @Test
    void shouldKeepSendingKeepaliveWhenOneStreamIsAlreadyCompleted() {
        streams.register(KEY);
        final RecordingEmitter completed = streams.lastEmitter();
        completed.failSends();
        streams.register(OTHER_KEY);
        final RecordingEmitter open = streams.lastEmitter();

        streams.heartbeat();

        assertThat(open.sent()).last().asString().contains(KEEPALIVE);
    }

    @Test
    void shouldCloseWithErrorWhenSendFails() throws IOException {
        final SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEventBuilder.class));

        streams.sendAndComplete(emitter, EVENT, DATA);

        verify(emitter).completeWithError(any(IOException.class));
        verify(emitter, never()).complete();
    }

    @Test
    void shouldCloseWithErrorWhenCompleteFails() {
        final SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("async request already completed")).when(emitter).complete();

        streams.sendAndComplete(emitter, EVENT, DATA);

        verify(emitter).completeWithError(any(IllegalStateException.class));
    }
}
