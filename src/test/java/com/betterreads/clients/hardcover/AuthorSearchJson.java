package com.betterreads.clients.hardcover;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class AuthorSearchJson {

    private static final String NAME = "name";

    private final ObjectNode json = Fixtures.parse("""
        {"data": {"search": {"results": {"hits": [
          {"document": {"id": "999", "name": "Dan Wells, Brandon Sanderson", "books_count": 0}},
          {"document": {"id": "204214", "name": "Brandon Sanderson", "books_count": 198}}
        ]}}}}
        """);

    private AuthorSearchJson() {
    }

    public static AuthorSearchJson authorSearch() {
        return new AuthorSearchJson();
    }

    public AuthorSearchJson withId(final String id) {
        HardcoverSearchHits.picked(json).put("id", id);
        return this;
    }

    public AuthorSearchJson withName(final String name) {
        HardcoverSearchHits.picked(json).put(NAME, name);
        return this;
    }

    public AuthorSearchJson withoutName() {
        HardcoverSearchHits.picked(json).remove(NAME);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
