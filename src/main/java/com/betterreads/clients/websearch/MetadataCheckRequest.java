package com.betterreads.clients.websearch;

import java.util.List;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;

public record MetadataCheckRequest(
    long bookId,
    String title,
    List<String> authors,
    @Nullable Integer year,
    @Nullable String seriesName,
    @Nullable Double seriesPosition,
    @Nullable String isbn13,
    @Nullable SeriesEntry universe,
    List<SeriesBook> seriesBooks,
    @Nullable String description
) {

    public MetadataCheckRequest {
        authors = List.copyOf(authors);
        seriesBooks = List.copyOf(seriesBooks);
    }
}
