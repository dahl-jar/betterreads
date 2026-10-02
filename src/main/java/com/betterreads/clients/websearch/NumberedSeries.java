package com.betterreads.clients.websearch;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class NumberedSeries {

    private static final int MAX_NAME_LENGTH = 200;

    private static final int MAX_POSITION = 999;

    private NumberedSeries() {
    }

    static @Nullable SeriesEntry from(final JsonNode field) {
        final String name = field.path("name").asString("").strip();
        final int number = field.path("number").asInt(0);
        final boolean valid = MetadataCheckMapper.isText(name, MAX_NAME_LENGTH)
            && MetadataCheckMapper.inRange(number, 1, MAX_POSITION);
        return valid ? new SeriesEntry(name, number) : null;
    }
}
