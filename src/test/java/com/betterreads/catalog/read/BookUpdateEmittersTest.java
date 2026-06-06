package com.betterreads.catalog.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.read.sse.BookUpdateEmitters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the emitter registry isolates subscribers by key, removes them after an update is
 * published, and tolerates a publish to a key nobody is watching.
 */
class BookUpdateEmittersTest {

    private static final String KEY = "9780000000001";

    private static final String OTHER_KEY = "9780000000002";

    private static final String TITLE = "Dune";

    private static final String AUTHOR = "Frank Herbert";

    private final BookUpdateEmitters emitters = new BookUpdateEmitters();

    @Test
    @DisplayName("registers an emitter under its key")
    void registersUnderKey() {
        emitters.register(KEY);

        assertThat(emitters.openCount(KEY)).isEqualTo(1);
    }

    @Test
    @DisplayName("removes the key's emitters after an update is published to it")
    void removesAfterPublish() {
        emitters.register(KEY);

        emitters.publish(KEY, detail());

        assertThat(emitters.openCount(KEY)).isZero();
    }

    @Test
    @DisplayName("publishing an update frees the stream's slot in the global count")
    void publishFreesGlobalSlot() {
        emitters.register(KEY);

        emitters.publish(KEY, detail());

        assertThat(emitters.openStreamCount())
            .as("publish completes the stream, so its slot returns to the global count rather than "
                + "leaking until every later cold stream hits the cap fallback")
            .isZero();
    }

    @Test
    @DisplayName("leaves a different key's subscribers untouched on publish")
    void isolatesByKey() {
        emitters.register(KEY);

        emitters.publish(OTHER_KEY, detail());

        assertThat(emitters.openCount(KEY)).isEqualTo(1);
    }

    @Test
    @DisplayName("tolerates a publish to a key with no subscribers")
    void publishWithoutSubscribers() {
        assertThatCode(() -> emitters.publish(KEY, detail())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("opening an incomplete book that completed during the open sends at once and leaves no stream")
    void openRechecksAfterRegistering() {
        emitters.open(KEY, incompleteDetail(), false, () -> Optional.of(detail()));

        assertThat(emitters.openCount(KEY)).isZero();
    }

    @Test
    @DisplayName("opening an incomplete book that is still incomplete holds the stream open")
    void openHoldsStreamForIncompleteBook() {
        emitters.open(KEY, incompleteDetail(), false, () -> Optional.of(incompleteDetail()));

        assertThat(emitters.openCount(KEY)).isEqualTo(1);
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
