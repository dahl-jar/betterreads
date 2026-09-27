package com.betterreads.clients.openlibrary;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class OpenLibrarySearchJson {

    static final String HOBBIT_WORK_KEY = "OL27482W";

    static final String HOBBIT_TITLE = "The Hobbit";

    static final int HOBBIT_FIRST_PUBLISHED = 1937;

    static final int HOBBIT_COVER_ID = 14_627_509;

    private static final String NUM_FOUND = "numFound";

    private static final String DOCS = "docs";

    private static final String TITLE = "title";

    private final ObjectNode json = Fixtures.parse("""
        {\
          "numFound": 1,\
          "docs": [\
            {"key": "/works/%s", "title": "%s", "first_publish_year": %d, "cover_i": %d}\
          ]\
        }\
        """.formatted(HOBBIT_WORK_KEY, HOBBIT_TITLE, HOBBIT_FIRST_PUBLISHED, HOBBIT_COVER_ID));

    private final ObjectNode template = ((ObjectNode) hits().get(0)).deepCopy();

    private OpenLibrarySearchJson() {
    }

    static OpenLibrarySearchJson search() {
        return new OpenLibrarySearchJson();
    }

    OpenLibrarySearchJson withoutHits() {
        json.put(NUM_FOUND, 0).putArray(DOCS);
        return this;
    }

    OpenLibrarySearchJson withHit(final String workKey, final String title) {
        hits().add(template.deepCopy().put("key", "/works/" + workKey).put(TITLE, title));
        json.put(NUM_FOUND, hits().size());
        return this;
    }

    OpenLibrarySearchJson withHit(final String workKey, final String title, final int firstPublishYear) {
        withHit(workKey, title);
        ((ObjectNode) hits().get(hits().size() - 1)).put("first_publish_year", firstPublishYear);
        return this;
    }

    OpenLibrarySearchJson withUntitledHit(final String workKey) {
        withHit(workKey, "");
        ((ObjectNode) hits().get(hits().size() - 1)).remove(TITLE);
        return this;
    }

    OpenLibrarySearchJson withoutDocs() {
        json.remove(DOCS);
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
