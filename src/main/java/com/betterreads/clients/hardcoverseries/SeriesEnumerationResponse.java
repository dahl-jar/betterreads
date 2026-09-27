package com.betterreads.clients.hardcoverseries;

import com.betterreads.booksource.NullableLists;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/** Shape is {@code data.series[].book_series[].book}, with every edition and translation at each position. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
record SeriesEnumerationResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable List<Series> series) {

        public Data {
            series = NullableLists.copyOf(series);
        }

        @Override
        @Nullable
        public List<Series> series() {
            return NullableLists.copyOf(series);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public record Series(
        @Nullable Integer primaryBooksCount,
        @Nullable List<BookSeries> bookSeries
    ) {

        public Series {
            bookSeries = NullableLists.copyOf(bookSeries);
        }

        @Override
        @Nullable
        public List<BookSeries> bookSeries() {
            return NullableLists.copyOf(bookSeries);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookSeries(@Nullable Double position, @Nullable HardcoverBookNode book) { }
}
