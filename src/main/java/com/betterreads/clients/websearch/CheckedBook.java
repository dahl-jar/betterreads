package com.betterreads.clients.websearch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.betterreads.book.VerifiedMetadata;
import tools.jackson.databind.JsonNode;

public record CheckedBook(VerifiedMetadata metadata, Map<String, FieldOutcome> outcomes, JsonNode answer) {

    public CheckedBook {
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    }

    public List<String> unreachableHosts() {
        return outcomes.entrySet().stream()
            .filter(outcome -> outcome.getValue() == FieldOutcome.PAGE_UNREACHABLE)
            .flatMap(outcome -> SourceHosts.hostOf(answer.path(outcome.getKey()).path("source").asString("")).stream())
            .toList();
    }
}
