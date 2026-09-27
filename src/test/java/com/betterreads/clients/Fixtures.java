package com.betterreads.clients;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class Fixtures {

    private static final JsonMapper MAPPER = new JsonMapper();

    private Fixtures() {
    }

    public static ObjectNode parse(final String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }

    public static <T> T convert(final JsonNode node, final Class<T> type) {
        return MAPPER.treeToValue(node, type);
    }
}
