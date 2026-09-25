package com.betterreads.integration.wikidata;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class WikidataSearchJson {

    private final ObjectNode json = Fixtures.json("wikidata/search.json");

    private WikidataSearchJson() {
    }

    public static WikidataSearchJson search() {
        return new WikidataSearchJson();
    }

    public WikidataSearchJson withOnlyHit(final String qid, final String label) {
        json.putArray("search").addObject().put("id", qid).put("label", label);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
