package com.betterreads.clients.loc;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * Reads a parsed LoC SRU MODS tree by local element name.
 *
 * <p>The records sit in a default XML namespace inside the {@code zs:} SRU wrapper. Jackson's XML
 * mapper folds element text into a {@code ""} child when the element also has attributes.
 */
final class LocSruTree {

    private static final Logger LOG = LoggerFactory.getLogger(LocSruTree.class);

    private static final XmlMapper XML = new XmlMapper();

    private static final String TEXT_FIELD = "";

    private static final String TYPE = "type";

    private LocSruTree() {
    }

    static Optional<JsonNode> parse(final String sruXml) {
        try {
            return Optional.ofNullable(XML.readTree(sruXml));
        } catch (JacksonException exception) {
            LOG.warn("loc.parse SRU response did not parse as XML", exception);
            return Optional.empty();
        }
    }

    /** a repeated tag parses as an array and a single one as a plain node, so both come back as a stream */
    static Stream<JsonNode> elements(final @Nullable JsonNode parent, final String tag) {
        final JsonNode node = parent == null ? null : parent.get(tag);
        if (node == null) {
            return Stream.empty();
        }
        return node.isArray() ? node.valueStream() : Stream.of(node);
    }

    /** depth-first, null if no descendant has the tag */
    static @Nullable JsonNode firstByTag(final @Nullable JsonNode root, final String tag) {
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

    static @Nullable String firstText(final @Nullable JsonNode root, final String tag) {
        return text(firstByTag(root, tag));
    }

    static @Nullable String attribute(final @Nullable JsonNode node, final String name) {
        return node == null ? null : asText(node.get(name));
    }

    /** trimmed text of a value node or an element with attributes, null if blank */
    static @Nullable String text(final @Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        final String value = node.isValueNode() ? node.asString() : asText(node.get(TEXT_FIELD));
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Stream<String> textOf(final @Nullable JsonNode node) {
        return Stream.ofNullable(text(node));
    }

    static Predicate<JsonNode> hasType(final String type) {
        return node -> type.equals(attribute(node, TYPE));
    }

    static Optional<Integer> intValue(final @Nullable String value) {
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
