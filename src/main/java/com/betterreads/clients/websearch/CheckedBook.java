package com.betterreads.clients.websearch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.betterreads.book.VerifiedMetadata;
import tools.jackson.databind.JsonNode;

public record CheckedBook(VerifiedMetadata metadata, Map<String, FieldOutcome> outcomes, JsonNode answer) {

    public CheckedBook {
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    }

    public boolean confirmed() {
        return outcomes.containsValue(FieldOutcome.CONFIRMED);
    }
}
