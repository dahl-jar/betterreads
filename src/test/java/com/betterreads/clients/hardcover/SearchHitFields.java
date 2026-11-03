package com.betterreads.clients.hardcover;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

public final class SearchHitFields {

    private static final String FEATURED_SERIES = "featured_series";

    private static final String POSITION = "position";

    private static final String READ_COUNT = "users_read_count";

    private static final String COMPILATION_FLAG = "compilation";

    private SearchHitFields() {
    }

    public static Consumer<ObjectNode> featuredSeries(final String name) {
        return featuredSeries(name, 1.0);
    }

    public static Consumer<ObjectNode> featuredSeries(final String name, final @Nullable Double position) {
        return hit -> hit.putObject(FEATURED_SERIES).put(POSITION, position).putObject("series").put("name", name);
    }

    public static Consumer<ObjectNode> unnamedFeaturedSeries() {
        return hit -> hit.putObject(FEATURED_SERIES).put(POSITION, 1.0);
    }

    public static Consumer<ObjectNode> compilation(final @Nullable Integer category) {
        return hit -> hit.put(COMPILATION_FLAG, true).put("book_category_id", category);
    }

    public static Consumer<ObjectNode> readCount(final int reads) {
        return hit -> hit.put(READ_COUNT, reads);
    }

    public static Consumer<ObjectNode> noReadCount() {
        return without(READ_COUNT);
    }

    public static Consumer<ObjectNode> noCompilationFlag() {
        return without(COMPILATION_FLAG);
    }

    public static Consumer<ObjectNode> noTitle() {
        return without(BookSearchJson.TITLE);
    }

    public static Consumer<ObjectNode> noIsbns() {
        return without(BookSearchJson.ISBNS);
    }

    public static Consumer<ObjectNode> noGenres() {
        return without("genres");
    }

    public static Consumer<ObjectNode> noAuthorNames() {
        return without(BookSearchJson.AUTHOR_NAMES);
    }

    private static Consumer<ObjectNode> without(final String field) {
        return hit -> hit.remove(field);
    }
}
