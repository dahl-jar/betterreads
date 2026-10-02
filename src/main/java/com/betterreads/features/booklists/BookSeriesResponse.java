package com.betterreads.features.booklists;

import java.util.List;

record BookSeriesResponse(String name, int position, List<SeriesBookResponse> books) {

    public BookSeriesResponse {
        books = List.copyOf(books);
    }

    @Override
    public List<SeriesBookResponse> books() {
        return List.copyOf(books);
    }
}
