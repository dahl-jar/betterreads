package com.betterreads.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class Fixtures {

    private static final JsonMapper MAPPER = new JsonMapper();

    private Fixtures() {
    }

    public static String read(final String path) {
        try (InputStream in = Objects.requireNonNull(
            Fixtures.class.getResourceAsStream("/fixtures/" + path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static ObjectNode json(final String path) {
        return (ObjectNode) MAPPER.readTree(read(path));
    }

    public static <T> T convert(final JsonNode node, final Class<T> type) {
        return MAPPER.treeToValue(node, type);
    }
}
