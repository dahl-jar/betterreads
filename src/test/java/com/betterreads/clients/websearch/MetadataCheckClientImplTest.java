package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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

    private static final String GOLDEN_SON = MetadataJson.GOLDEN_SON;

    private static final String SONS_OF_ARES = "Sons of Ares";

    private static final String EXIT = "exit 1";

    private static final SearchAttempt NO_ANSWER = new SearchAttempt.Failed(EXIT);

    private static final String LIMIT = "You have hit your session limit";

    private final WebSearchRunner runner = mock(WebSearchRunner.class);

    private final SourcePageFetcher fetcher = mock(SourcePageFetcher.class);

    private final MetadataCheckClientImpl client =
        new MetadataCheckClientImpl(runner, WebSearchSamples.properties("claude", Duration.ofMinutes(5)), fetcher);

    private static MetadataCheckRequest redRising(final String isbn) {
        return MetadataJson.request(MetadataJson.BOOK_ID, isbn);
    }

    private String prompt(final MetadataCheckRequest... books) {
        when(runner.run(anyString(), anyString(), anyList())).thenReturn(NO_ANSWER);
        final ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
        client.check(List.of(books), SourceGroup.SFF);
        verify(runner).run(prompt.capture(), anyString(), anyList());
        return prompt.getValue();
    }

    @Nested
    class Prompt {

        @Test
        void shouldListEveryBookInThePrompt() {
            final MetadataCheckRequest other = MetadataJson.request(OTHER_ID, GOLDEN_SON, 2.0, null);

            final String prompt = prompt(redRising(MetadataJson.ISBN), other);

            assertThat(prompt)
                .contains("{\"id\":1,\"title\":\"Red Rising\",\"authors\":[\"Pierce Brown\"],"
                    + "\"year\":2014,\"series\":\"Red Rising Saga\",\"number\":1,\"seriesBooks\":[],\"universe\":null,"
                    + "\"universeNumber\":null,\"isbn13\":\"9780345539786\",\"isbnIsEnglish\":true,"
                    + "\"description\":\"Darrow is a Red")
                .contains("{\"id\":2,\"title\":\"Golden Son\"");
        }

        @Test
        void shouldSendTheOtherBooksOfTheSeries() {
            final List<SeriesBook> others = List.of(
                new SeriesBook(GOLDEN_SON, 2.0),
                new SeriesBook(SONS_OF_ARES, NOVELLA_NUMBER));
            final MetadataCheckRequest book = MetadataJson.inSeries(redRising(MetadataJson.ISBN), null, others);

            final String prompt = prompt(book);

            assertThat(prompt).contains("\"seriesBooks\":[{\"title\":\"Golden Son\",\"number\":2},"
                + "{\"title\":\"Sons of Ares\",\"number\":0.5}]");
        }

        @Test
        void shouldAskToKeepTheStoredSeriesName() {
            final String prompt = prompt(redRising(MetadataJson.ISBN));

            assertThat(prompt.replace('\n', ' '))
                .contains("seriesBooks lists the other books our catalog holds in the stored series. If the stored "
                    + "series name is a name the publisher or Wikipedia uses for this series, return the stored name. "
                    + "Change it only when the book belongs to a different series.");
        }

        @Test
        void shouldEndWithTheThinkInstructionAfterTheBooks() {
            final String prompt = prompt(redRising(MetadataJson.ISBN));

            assertThat(prompt.strip()).endsWith(MetadataCheckClientImpl.CLOSING);
        }

        @Test
        void shouldSendTheStoredUniverse() {
            final MetadataCheckRequest inUniverse =
                MetadataJson.inSeries(redRising(MetadataJson.ISBN), MetadataJson.UNIVERSE_ENTRY, List.of());

            final String prompt = prompt(inUniverse);

            assertThat(prompt).contains("\"universe\":\"Red Rising Universe\",\"universeNumber\":4");
        }

        @Test
        void shouldSendADecimalNumber() {
            final MetadataCheckRequest novella =
                MetadataJson.request(OTHER_ID, SONS_OF_ARES, NOVELLA_NUMBER, null);

            final String prompt = prompt(novella);

            assertThat(prompt).contains("\"number\":0.5,");
        }

        @Test
        void shouldFlagForeignIsbn() {
            final String prompt = prompt(redRising(GERMAN_ISBN));

            assertThat(prompt).contains("\"isbnIsEnglish\":false");
        }
    }

    @Nested
    class Schema {

        private JsonNode askedSchema() {
            when(runner.run(anyString(), anyString(), anyList())).thenReturn(NO_ANSWER);
            final ArgumentCaptor<String> schema = ArgumentCaptor.captor();
            client.check(List.of(redRising(MetadataJson.ISBN)), SourceGroup.SFF);
            verify(runner).run(anyString(), schema.capture(), anyList());
            return new JsonMapper().readTree(schema.getValue());
        }

        @Test
        void shouldAskForTheUniverse() {
            final JsonNode schema = askedSchema();

            final JsonNode book = schema.at("/properties/books/items");
            assertThat(book.at("/required").valueStream().map(JsonNode::asString)).contains(UNIVERSE_KEY);
            assertThat(book.at("/properties/universe/properties/value/required").valueStream().map(JsonNode::asString))
                .containsExactly("name", NUMBER);
        }

        @Test
        void shouldAskForADecimalNumber() {
            final JsonNode schema = askedSchema();

            final JsonNode number =
                schema.at("/properties/books/items/properties/series/properties/value/properties/number/type");
            assertThat(number.asString()).isEqualTo(NUMBER);
        }
    }

    @Nested
    class Result {

        private CheckOutcome answered() {
            when(runner.run(anyString(), anyString(), anyList()))
                .thenReturn(new SearchAttempt.Answered(MetadataJson.metadata().node(), WebSearchSamples.USAGE));
            when(fetcher.page(anyString())).thenReturn(Optional.of(MetadataJson.sourcePage()));
            return client.check(List.of(redRising(GERMAN_ISBN)), SourceGroup.SFF);
        }

        @Test
        void shouldReturnMetadataById() {
            final CheckOutcome result = answered();

            assertThat(result).isInstanceOfSatisfying(CheckOutcome.Checked.class, checked ->
                assertThat(checked.run().books()).extractingByKey(MetadataJson.BOOK_ID)
                    .extracting(book -> book.metadata().isbn13()).isEqualTo(MetadataJson.ISBN));
        }

        @Test
        void shouldReturnTheRunUsage() {
            final CheckOutcome result = answered();

            assertThat(result).isInstanceOfSatisfying(CheckOutcome.Checked.class, checked ->
                assertThat(checked.run().usage()).isEqualTo(WebSearchSamples.USAGE));
        }

        @Test
        void shouldStopTheRunWhenTheSearchHalts() {
            when(runner.run(anyString(), anyString(), anyList())).thenReturn(new SearchAttempt.Halted(LIMIT));

            final CheckOutcome result = client.check(List.of(redRising(GERMAN_ISBN)), SourceGroup.SFF);

            assertThat(result).isEqualTo(new CheckOutcome.RunHalted(LIMIT));
        }

        @Test
        void shouldFailOnlyTheBatchWhenTheSearchFails() {
            when(runner.run(anyString(), anyString(), anyList())).thenReturn(NO_ANSWER);

            final CheckOutcome result = client.check(List.of(redRising(GERMAN_ISBN)), SourceGroup.SFF);

            assertThat(result).isEqualTo(new CheckOutcome.BatchFailed(EXIT));
        }

        @Test
        void shouldSearchOnlyTheGroupDomains() {
            when(runner.run(anyString(), anyString(), anyList())).thenReturn(NO_ANSWER);

            client.check(List.of(redRising(GERMAN_ISBN)), SourceGroup.GENERAL);

            verify(runner).run(anyString(), anyString(), eq(List.of("en.wikipedia.org")));
        }
    }
}
