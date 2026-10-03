package com.betterreads.clients.websearch;

import java.util.Map;

public record CheckRun(Map<Long, CheckedBook> books, SearchUsage usage) {

    public CheckRun {
        books = Map.copyOf(books);
    }
}
