package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MetadataCheckClientImplTest {

    private static final String GERMAN_ISBN = "9783453315617";

    private static final long OTHER_ID = 2L;

    private static final String UNIVERSE_KEY = "universe";

    private static final int GOLDEN_SON_YEAR = 2015;

    private final WebSearchRunner runner = mock(WebSearchRunner.class);

    private final MetadataCheckClientImpl client =
        new MetadataCheckClientImpl(runner, WebSearchSamples.properties("claude", Duration.ofMinutes(5)));

    private static MetadataCheckRequest redRising(final String isbn) {
        return redRising(isbn, null);
    }

    private static MetadataCheckRequest redRising(final String isbn, final @Nullable SeriesEntry universe) {
        return new MetadataCheckRequest(MetadataJson.BOOK_ID, MetadataJson.TITLE,
            List.of(MetadataJson.AUTHOR), MetadataJson.YEAR, MetadataJson.SERIES, 1, isbn, universe);
    }

    @Test
    void shouldListEveryBookInThePrompt() {
        when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
        final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
        final MetadataCheckRequest other = new MetadataCheckRequest(OTHER_ID, "Golden Son",
            List.of(MetadataJson.AUTHOR), GOLDEN_SON_YEAR, MetadataJson.SERIES, 2, null, null);

        client.check(List.of(redRising(MetadataJson.ISBN), other));

        verify(runner).run(prompt.capture(), anyString());
        assertThat(prompt.getValue())
            .contains("{\"id\":1,\"title\":\"Red Rising\",\"authors\":[\"Pierce Brown\"],"
                + "\"year\":2014,\"series\":\"Red Rising Saga\",\"number\":1,\"universe\":null,"
                + "\"universeNumber\":null,\"isbn13\":\"9780345539786\",\"isbnIsEnglish\":true}")
            .contains("{\"id\":2,\"title\":\"Golden Son\"");
    }

    @Test
    void shouldSendTheStoredUniverse() {
        when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
        final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
        final MetadataCheckRequest inUniverse = redRising(MetadataJson.ISBN, MetadataJson.UNIVERSE_ENTRY);

        client.check(List.of(inUniverse));

        verify(runner).run(prompt.capture(), anyString());
        assertThat(prompt.getValue()).contains("\"universe\":\"Red Rising Universe\",\"universeNumber\":4");
    }

    @Test
    void shouldAskForTheUniverse() {
        when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
        final ArgumentCaptor<String> schema = ArgumentCaptor.captor();

        client.check(List.of(redRising(MetadataJson.ISBN)));

        verify(runner).run(anyString(), schema.capture());
        final JsonNode book = new JsonMapper().readTree(schema.getValue()).at("/properties/books/items");
        assertThat(book.at("/required").valueStream().map(JsonNode::asString)).contains(UNIVERSE_KEY);
        assertThat(book.at("/properties/universe/required").valueStream().map(JsonNode::asString))
            .containsExactly("name", "number", "source");
    }

    @Test
    void shouldFlagForeignIsbn() {
        when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
        final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();

        client.check(List.of(redRising(GERMAN_ISBN)));

        verify(runner).run(prompt.capture(), anyString());
        assertThat(prompt.getValue()).contains("\"isbnIsEnglish\":false");
    }

    @Test
    void shouldReturnMetadataById() {
        when(runner.run(anyString(), anyString())).thenReturn(Optional.of(MetadataJson.metadata().node()));

        final Optional<Map<Long, VerifiedMetadata>> checks = client.check(List.of(redRising(GERMAN_ISBN)));

        assertThat(checks).get().satisfies(map -> assertThat(map).extractingByKey(MetadataJson.BOOK_ID)
            .extracting(VerifiedMetadata::isbn13).isEqualTo(MetadataJson.ISBN));
    }
}
