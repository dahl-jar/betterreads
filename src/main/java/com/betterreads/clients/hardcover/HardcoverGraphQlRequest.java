package com.betterreads.clients.hardcover;

import java.util.Map;

public record HardcoverGraphQlRequest(String query, Map<String, Object> variables) {

    public HardcoverGraphQlRequest {
        variables = Map.copyOf(variables);
    }

    @Override
    public Map<String, Object> variables() {
        return Map.copyOf(variables);
    }
}
