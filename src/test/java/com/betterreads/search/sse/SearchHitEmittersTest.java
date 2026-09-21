package com.betterreads.search.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.search.dto.BookSearchDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchHitEmittersTest {

    private static final String QUERY_KEY = "robert jordan";

    private static final String RAW_QUERY = "  Robert Jordan ";

    private static final String OTHER_KEY = "dune";

    private static final int MAX_OPEN_STREAMS = 500;

    private final SearchHitEmitters emitters = new SearchHitEmitters();

    @Test
    void shouldListOpenQueriesByNormalizedKey() {
        emitters.open(RAW_QUERY);

        assertThat(emitters.openQueries()).containsExactly(QUERY_KEY);
    }

    @Test
    void shouldKeepStreamsOpenAfterPublishingHit() {
        emitters.open(RAW_QUERY);

        emitters.publish(QUERY_KEY, hit());

        assertThat(emitters.openQueries()).containsExactly(QUERY_KEY);
    }

    @Test
    void shouldNotRegisterStreamPastCapacity() {
        for (int i = 0; i < MAX_OPEN_STREAMS; i++) {
            emitters.open(QUERY_KEY + i);
        }

        emitters.open(OTHER_KEY);

        assertThat(emitters.openQueries()).doesNotContain(OTHER_KEY);
    }

    private static BookSearchDocument hit() {
        return BookSearchDocument.builder("9780000000001")
            .title("The Eye of the World")
            .authors(List.of("Robert Jordan"))
            .build();
    }
}
