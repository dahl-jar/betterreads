package com.betterreads.clients.websearch;

import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SeriesNumber;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class NumberedSeries {

    private static final int MAX_NAME_LENGTH = 200;

    private static final int MAX_POSITION = 999;

    private NumberedSeries() {
    }

    static @Nullable SeriesEntry from(final JsonNode field) {
        final String name = field.path("name").asString("").strip();
        return SeriesNumber.of(field.path("number").asDouble(0))
            .filter(number -> number <= MAX_POSITION && MetadataCheckMapper.isText(name, MAX_NAME_LENGTH))
            .map(number -> new SeriesEntry(name, number))
            .orElse(null);
    }
}
