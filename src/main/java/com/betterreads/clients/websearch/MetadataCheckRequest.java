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
    @Nullable Integer seriesPosition,
    @Nullable String isbn13,
    @Nullable SeriesEntry universe
) {

    public MetadataCheckRequest {
        authors = List.copyOf(authors);
    }
}
