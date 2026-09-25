package com.betterreads.integration.itunes;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class ItunesSearchJson {

    private static final String RESULT_COUNT = "resultCount";

    private static final String RESULTS = "results";

    private static final String TRACK_NAME = "trackName";

    private static final String DESCRIPTION = "description";

    private final ObjectNode json = Fixtures.json("itunes/search.json");

    private final ObjectNode template = ((ObjectNode) entries().get(0)).deepCopy();

    private ItunesSearchJson() {
    }

    public static ItunesSearchJson search() {
        return new ItunesSearchJson();
    }

    public ItunesSearchJson withoutResults() {
        json.put(RESULT_COUNT, 0).putArray(RESULTS);
        return this;
    }

    public ItunesSearchJson withTrackName(final String trackName) {
        ((ObjectNode) entries().get(0)).put(TRACK_NAME, trackName);
        return this;
    }

    public ItunesSearchJson withDescription(final String description) {
        ((ObjectNode) entries().get(0)).put(DESCRIPTION, description);
        return this;
    }

    public ItunesSearchJson withResult(final String trackName, final String description) {
        entries().add(template.deepCopy().put(TRACK_NAME, trackName).put(DESCRIPTION, description));
        json.put(RESULT_COUNT, entries().size());
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ArrayNode entries() {
        return json.withArray(RESULTS);
    }
}
