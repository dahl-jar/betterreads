package com.betterreads.integration.hardcover;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class AuthorSearchJson {

    private final ObjectNode json = Fixtures.json("hardcover/author-search.json");

    private AuthorSearchJson() {
    }

    public static AuthorSearchJson authorSearch() {
        return new AuthorSearchJson();
    }

    public AuthorSearchJson withoutHits() {
        SearchHits.clear(json);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
