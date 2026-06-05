package com.betterreads.integration.hardcover.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Hardcover series enumeration response.
 *
 * <p>Chain: {@code data.series[].book_series[].book}. Each position carries every edition and
 * translation, so the mapper collapses them to one volume per position.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record SeriesEnumerationResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable List<Series> series) {

        public Data {
            if (series != null) {
                series = List.copyOf(series);
            }
        }

        @Override
        @Nullable
        public List<Series> series() {
            return series == null ? null : List.copyOf(series);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public record Series(
        @Nullable String name,
        @Nullable Integer primaryBooksCount,
        @Nullable List<BookSeries> bookSeries
    ) {

        public Series {
            if (bookSeries != null) {
                bookSeries = List.copyOf(bookSeries);
            }
        }

        @Override
        @Nullable
        public List<BookSeries> bookSeries() {
            return bookSeries == null ? null : List.copyOf(bookSeries);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookSeries(@Nullable Double position, @Nullable HardcoverBookNode book) { }
}
