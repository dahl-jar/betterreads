package com.betterreads.clients.websearch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

record FieldVerdicts(
    List<FieldVerdict> fields,
    DescriptionVerdict description,
    Map<String, FieldOutcome> outcomes,
    JsonNode answer
) {

    FieldVerdicts {
        fields = List.copyOf(fields);
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    }
}
