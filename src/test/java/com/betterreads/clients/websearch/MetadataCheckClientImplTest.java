package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MetadataCheckClientImplTest {

    private static final String GERMAN_ISBN = MetadataJson.GERMAN_ISBN;

    private static final long OTHER_ID = MetadataJson.OTHER_ID;

    private static final String UNIVERSE_KEY = "universe";

    private static final String NUMBER = "number";

    private static final double NOVELLA_NUMBER = 0.5;

    private static final int GOLDEN_SON_YEAR = 2015;

    private static final String GOLDEN_SON = MetadataJson.GOLDEN_SON;

    private static final String SONS_OF_ARES = "Sons of Ares";

    private final WebSearchRunner runner = mock(WebSearchRunner.class);

    private final MetadataCheckClientImpl client =
        new MetadataCheckClientImpl(runner, WebSearchSamples.properties("claude", Duration.ofMinutes(5)));

    private static MetadataCheckRequest redRising(final String isbn) {
        return redRising(isbn, null);
    }

    private static MetadataCheckRequest redRising(final String isbn, final @Nullable SeriesEntry universe) {
        return redRising(isbn, universe, List.of());
    }

    private static MetadataCheckRequest redRising(
        final String isbn, final @Nullable SeriesEntry universe, final List<SeriesBook> others) {
        return new MetadataCheckRequest(MetadataJson.BOOK_ID, MetadataJson.TITLE,
            List.of(MetadataJson.AUTHOR), MetadataJson.YEAR, MetadataJson.SERIES, 1.0, isbn, universe, others);
    }

    @Nested
    class Prompt {

        @Test
        void shouldListEveryBookInThePrompt() {
            when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
            final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
            final MetadataCheckRequest other = new MetadataCheckRequest(OTHER_ID, GOLDEN_SON,
                List.of(MetadataJson.AUTHOR), GOLDEN_SON_YEAR, MetadataJson.SERIES, 2.0, null, null, List.of());

            client.check(List.of(redRising(MetadataJson.ISBN), other));

            verify(runner).run(prompt.capture(), anyString());
            assertThat(prompt.getValue())
                .contains("{\"id\":1,\"title\":\"Red Rising\",\"authors\":[\"Pierce Brown\"],"
                    + "\"year\":2014,\"series\":\"Red Rising Saga\",\"number\":1,\"seriesBooks\":[],\"universe\":null,"
                    + "\"universeNumber\":null,\"isbn13\":\"9780345539786\",\"isbnIsEnglish\":true}")
                .contains("{\"id\":2,\"title\":\"Golden Son\"");
        }

        @Test
        void shouldSendTheOtherBooksOfTheSeries() {
            when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
            final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
            final List<SeriesBook> others = List.of(
                new SeriesBook(GOLDEN_SON, 2.0),
                new SeriesBook(SONS_OF_ARES, NOVELLA_NUMBER));

            client.check(List.of(redRising(MetadataJson.ISBN, null, others)));

            verify(runner).run(prompt.capture(), anyString());
            assertThat(prompt.getValue())
                .contains("\"seriesBooks\":[{\"title\":\"Golden Son\",\"number\":2},"
                    + "{\"title\":\"Sons of Ares\",\"number\":0.5}]");
        }

        @Test
        void shouldAskToKeepTheStoredSeriesName() {
            when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
            final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();

            client.check(List.of(redRising(MetadataJson.ISBN)));

            verify(runner).run(prompt.capture(), anyString());
            assertThat(prompt.getValue().replace('\n', ' '))
                .contains("seriesBooks lists the other books our catalog holds in the stored series. If the stored "
                    + "series name is a name the publisher or Wikipedia uses for this series, return the stored name. "
                    + "Change it only when the book belongs to a different series.");
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
        void shouldSendADecimalNumber() {
            when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
            final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
            final MetadataCheckRequest novella = new MetadataCheckRequest(OTHER_ID, SONS_OF_ARES,
                List.of(MetadataJson.AUTHOR), GOLDEN_SON_YEAR, MetadataJson.SERIES, NOVELLA_NUMBER, null, null,
                List.of());

            client.check(List.of(novella));

            verify(runner).run(prompt.capture(), anyString());
            assertThat(prompt.getValue()).contains("\"number\":0.5,");
        }

        @Test
        void shouldFlagForeignIsbn() {
            when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
            final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();

            client.check(List.of(redRising(GERMAN_ISBN)));

            verify(runner).run(prompt.capture(), anyString());
            assertThat(prompt.getValue()).contains("\"isbnIsEnglish\":false");
        }
    }

    @Nested
    class Schema {

        private JsonNode askedSchema() {
            when(runner.run(anyString(), anyString())).thenReturn(Optional.empty());
            final ArgumentCaptor<String> schema = ArgumentCaptor.captor();
            client.check(List.of(redRising(MetadataJson.ISBN)));
            verify(runner).run(anyString(), schema.capture());
            return new JsonMapper().readTree(schema.getValue());
        }

        @Test
        void shouldAskForTheUniverse() {
            final JsonNode schema = askedSchema();

            final JsonNode book = schema.at("/properties/books/items");
            assertThat(book.at("/required").valueStream().map(JsonNode::asString)).contains(UNIVERSE_KEY);
            assertThat(book.at("/properties/universe/required").valueStream().map(JsonNode::asString))
                .containsExactly("name", NUMBER, "source");
        }

        @Test
        void shouldAskForADecimalNumber() {
            final JsonNode schema = askedSchema();

            final JsonNode number = schema.at("/properties/books/items/properties/series/properties/number/type");
            assertThat(number.valueStream().map(JsonNode::asString)).containsExactly(NUMBER, "null");
        }
    }

    @Nested
    class Result {

        private void givenAnswer() {
            when(runner.run(anyString(), anyString()))
                .thenReturn(Optional.of(new WebSearchResult(MetadataJson.metadata().node(), WebSearchSamples.USAGE)));
        }

        @Test
        void shouldReturnMetadataById() {
            givenAnswer();

            final Optional<CheckRun> result = client.check(List.of(redRising(GERMAN_ISBN)));

            assertThat(result).get().satisfies(run -> assertThat(run.books()).extractingByKey(MetadataJson.BOOK_ID)
                .extracting(book -> book.metadata().isbn13()).isEqualTo(MetadataJson.ISBN));
        }

        @Test
        void shouldReturnTheRunUsage() {
            givenAnswer();

            final Optional<CheckRun> result = client.check(List.of(redRising(GERMAN_ISBN)));

            assertThat(result).get().extracting(CheckRun::usage).isEqualTo(WebSearchSamples.USAGE);
        }
    }
}
