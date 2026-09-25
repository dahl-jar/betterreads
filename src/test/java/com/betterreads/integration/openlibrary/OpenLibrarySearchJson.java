package com.betterreads.integration.openlibrary;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class OpenLibrarySearchJson {

    private static final String NUM_FOUND = "numFound";

    private static final String DOCS = "docs";

    private final ObjectNode json = Fixtures.json("openlibrary/search.json");

    private final ObjectNode template = ((ObjectNode) hits().get(0)).deepCopy();

    private OpenLibrarySearchJson() {
    }

    public static OpenLibrarySearchJson search() {
        return new OpenLibrarySearchJson();
    }

    public OpenLibrarySearchJson withoutHits() {
        json.put(NUM_FOUND, 0).putArray(DOCS);
        return this;
    }

    public OpenLibrarySearchJson withHit(final String workKey, final String title) {
        hits().add(template.deepCopy().put("key", "/works/" + workKey).put("title", title));
        json.put(NUM_FOUND, hits().size());
        return this;
    }

    public OpenLibrarySearchJson withHit(final String workKey, final String title, final int firstPublishYear) {
        withHit(workKey, title);
        ((ObjectNode) hits().get(hits().size() - 1)).put("first_publish_year", firstPublishYear);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ArrayNode hits() {
        return json.withArray(DOCS);
    }
}
