package com.betterreads.integration.loc;

import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Navigates a parsed LoC SRU tree (MODS or MARC) by local element name.
 *
 * <p>The records sit in a default XML namespace inside the {@code zs:} SRU wrapper. Jackson's XML
 * mapper parses repeated elements as either a single node or an array, and folds element text into a
 * {@code ""} child when the element also carries attributes. These helpers smooth over both shapes
 * so callers read fields without minding namespaces or arity.
 */
public final class SruTree {

    private static final String TEXT_FIELD = "";

    private SruTree() {
    }

    /** Returns the children with the given tag, treating a single node as a one-element stream. */
    public static Stream<JsonNode> elements(final @Nullable JsonNode parent, final String tag) {
        final JsonNode node = parent == null ? null : parent.get(tag);
        if (node == null) {
            return Stream.empty();
        }
        return node.isArray() ? node.valueStream() : Stream.of(node);
    }

    /** Returns every descendant with the given tag at any depth, in document order. */
    public static Stream<JsonNode> descendants(final @Nullable JsonNode root, final String tag) {
        if (root == null) {
            return Stream.empty();
        }
        final Stream<JsonNode> here = elements(root, tag);
        final Stream<JsonNode> deeper = root.valueStream()
            .flatMap(child -> descendants(child, tag));
        return Stream.concat(here, deeper);
    }

    /** Returns the first descendant with the given tag, searching depth-first, or null if none. */
    public static @Nullable JsonNode firstByTag(final @Nullable JsonNode root, final String tag) {
        if (root == null) {
            return null;
        }
        if (root.has(tag)) {
            return root.get(tag);
        }
        return root.valueStream()
            .map(child -> firstByTag(child, tag))
            .filter(found -> found != null)
            .findFirst()
            .orElse(null);
    }

    /** Returns the first descendant text for the given tag, or null. */
    public static @Nullable String firstText(final @Nullable JsonNode root, final String tag) {
        return text(firstByTag(root, tag));
    }

    /** Returns the value of the named attribute, or null. */
    public static @Nullable String attribute(final @Nullable JsonNode node, final String name) {
        return node == null ? null : asText(node.get(name));
    }

    /** Returns the trimmed text of a value node or an attribute-bearing element, or null if blank. */
    public static @Nullable String text(final @Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        final String value = node.isValueNode() ? node.asString() : asText(node.get(TEXT_FIELD));
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Returns the integer value of a tag's text, or null if absent or non-numeric. */
    public static Optional<Integer> intValue(final @Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static @Nullable String asText(final @Nullable JsonNode node) {
        return node == null ? null : node.asString();
    }
}
