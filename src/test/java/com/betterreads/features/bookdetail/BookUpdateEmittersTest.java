package com.betterreads.features.bookdetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.betterreads.sse.RecordingSseStreams;
import com.betterreads.sse.RecordingSseStreams.RecordingEmitter;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// PMD.TooManyMethods: one test per stream lifecycle case plus the shared fixtures and assertion.
@SuppressWarnings("PMD.TooManyMethods")
class BookUpdateEmittersTest {

    private static final String KEY = "9780000000001";

    private static final String OTHER_KEY = "9780000000002";

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private static final int STREAM_CAP = 500;

    private static final String UPDATE_EVENT = "event:book-updated";

    private final RecordingSseStreams streams = new RecordingSseStreams();

    private final BookUpdateEmitters emitters = new BookUpdateEmitters(streams);

    @Test
    void shouldCloseStreamAtOnceWhenBookIsComplete() {
        emitters.open(KEY, detail(), Optional::empty);
        final RecordingEmitter emitter = streams.lastEmitter();

        assertSentOnceAndClosed(emitter);
    }

    @Test
    void shouldCloseStreamWhenUpdateIsPublished() {
        emitters.open(KEY, incompleteDetail(), Optional::empty);
        final RecordingEmitter emitter = streams.lastEmitter();

        emitters.publish(KEY, detail());

        assertThat(emitter.sent()).last().asString().startsWith(UPDATE_EVENT);
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    @DisplayName("removes the key's emitters after an update is published to it")
    void removesAfterPublish() {
        openIncomplete();

        emitters.publish(KEY, detail());

        assertThat(streams.openKeys()).doesNotContain(KEY);
    }

    @Test
    @DisplayName("publishing an update frees the stream's slot in the global count")
    void publishFreesGlobalSlot() {
        IntStream.range(0, STREAM_CAP).forEach(ignored -> openIncomplete());
        assertThat(streams.atCapacity()).isTrue();

        emitters.publish(KEY, detail());

        assertThat(streams.atCapacity())
            .as("publish completes the streams, so their slots return to the global count")
            .isFalse();
    }

    @Test
    void shouldSendBookAtOnceWhenStreamsAreAtCapacity() {
        IntStream.range(0, STREAM_CAP)
            .forEach(ignored -> emitters.open(OTHER_KEY, incompleteDetail(), Optional::empty));

        emitters.open(KEY, incompleteDetail(), Optional::empty);
        final RecordingEmitter emitter = streams.lastEmitter();

        assertSentOnceAndClosed(emitter);
        assertThat(streams.openKeys()).doesNotContain(KEY);
    }

    @Test
    @DisplayName("leaves a different key's subscribers untouched on publish")
    void isolatesByKey() {
        openIncomplete();

        emitters.publish(OTHER_KEY, detail());

        assertThat(streams.openKeys()).containsExactly(KEY);
    }

    @Test
    @DisplayName("tolerates a publish to a key with no subscribers")
    void publishWithoutSubscribers() {
        assertThatCode(() -> emitters.publish(KEY, detail())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("opening an incomplete book that completed during the open leaves no stream")
    void openRechecksAfterRegistering() {
        emitters.open(KEY, incompleteDetail(), () -> Optional.of(detail()));

        assertThat(streams.openKeys()).doesNotContain(KEY);
    }

    @Test
    @DisplayName("opening an incomplete book that has not completed holds the stream open")
    void openHoldsStreamForIncompleteBook() {
        emitters.open(KEY, incompleteDetail(), () -> Optional.of(incompleteDetail()));

        assertThat(streams.openKeys()).containsExactly(KEY);
    }

    private static void assertSentOnceAndClosed(final RecordingEmitter emitter) {
        assertThat(emitter.sent()).singleElement().asString().startsWith(UPDATE_EVENT);
        assertThat(emitter.completed()).isTrue();
    }

    private void openIncomplete() {
        emitters.open(KEY, incompleteDetail(), Optional::empty);
    }

    private static BookDetailResponse detail() {
        return BookDetailResponse.builder(KEY, true)
            .title(TITLE)
            .authors(List.of(AUTHOR))
            .build();
    }

    private static BookDetailResponse incompleteDetail() {
        return BookDetailResponse.builder(KEY, false)
            .title(TITLE)
            .authors(List.of(AUTHOR))
            .build();
    }
}
