package com.betterreads.clients.wikidata;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class WikidataSearchJson {

    private final ObjectNode json = Fixtures.parse("""
        {"search": [{"id": "Q60834962", "label": "Dune"}, {"id": "Q190192", "label": "Dune"}]}
        """);

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
