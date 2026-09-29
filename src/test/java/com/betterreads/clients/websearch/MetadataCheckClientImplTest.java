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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetadataCheckClientImplTest {

    private static final String GERMAN_ISBN = "9783453315617";

    private static final long OTHER_ID = 2L;

    private static final int GOLDEN_SON_YEAR = 2015;

    private final WebSearchRunner runner = mock(WebSearchRunner.class);

    private final MetadataCheckClientImpl client =
        new MetadataCheckClientImpl(runner, WebSearchSamples.properties("claude", Duration.ofMinutes(5)));

    private static MetadataCheckRequest redRising(final String isbn) {
        return new MetadataCheckRequest(MetadataJson.BOOK_ID, MetadataJson.TITLE,
            List.of(MetadataJson.AUTHOR), MetadataJson.YEAR, MetadataJson.SERIES, 1, isbn);
    }

    @Test
    void shouldListEveryBookInThePrompt() {
        when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
        final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
        final MetadataCheckRequest other = new MetadataCheckRequest(OTHER_ID, "Golden Son",
            List.of(MetadataJson.AUTHOR), GOLDEN_SON_YEAR, MetadataJson.SERIES, 2, null);

        client.check(List.of(redRising(MetadataJson.ISBN), other));

        verify(runner).run(prompt.capture(), anyString());
        assertThat(prompt.getValue())
            .contains("{\"id\":1,\"title\":\"Red Rising\",\"authors\":[\"Pierce Brown\"],"
                + "\"year\":2014,\"series\":\"Red Rising Saga\",\"number\":1,\"isbn13\":\"9780345539786\","
                + "\"isbnIsEnglish\":true}")
            .contains("{\"id\":2,\"title\":\"Golden Son\"");
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
