package com.betterreads.integration.hardcover.dto;

import java.util.Map;

/** GraphQL POST body: a query string and its variables. */
public record GraphQlRequest(String query, Map<String, Object> variables) {

    public GraphQlRequest {
        variables = Map.copyOf(variables);
    }

    @Override
    public Map<String, Object> variables() {
        return Map.copyOf(variables);
    }
}
