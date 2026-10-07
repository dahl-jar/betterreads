package com.betterreads.features.search;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.DocumentsQuery;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class IndexIds {

    private IndexIds() {
    }

    static Stream<JsonNode> all(
        final Index index, final ObjectMapper mapper, final String primaryKey, final int pageSize
    ) {
        return Stream.iterate(0, offset -> offset + pageSize)
            .map(offset -> page(index, mapper, primaryKey, new DocumentsQuery().setOffset(offset).setLimit(pageSize)))
            .takeWhile(page -> !page.isEmpty())
            .flatMap(List::stream);
    }

    static Stream<JsonNode> among(
        final Index index, final ObjectMapper mapper, final String primaryKey, final Collection<String> ids
    ) {
        final String quoted = ids.stream().map(IndexIds::quoted).collect(Collectors.joining(", "));
        final DocumentsQuery query = new DocumentsQuery()
            .setLimit(ids.size())
            .setFilter(new String[] {primaryKey + " IN [" + quoted + "]"});
        return page(index, mapper, primaryKey, query).stream();
    }

    static String quoted(final String id) {
        return '"' + id.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static List<JsonNode> page(
        final Index index, final ObjectMapper mapper, final String primaryKey, final DocumentsQuery query
    ) {
        final String json = index.getRawDocuments(query.setFields(new String[] {primaryKey}));
        return mapper.readTree(json).path("results").valueStream()
            .map(document -> document.path(primaryKey))
            .toList();
    }
}
