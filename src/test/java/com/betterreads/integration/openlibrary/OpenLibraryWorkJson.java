package com.betterreads.integration.openlibrary;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class OpenLibraryWorkJson {

    private final ObjectNode json = Fixtures.json("openlibrary/work.json");

    private OpenLibraryWorkJson() {
    }

    public static OpenLibraryWorkJson work() {
        return new OpenLibraryWorkJson();
    }

    public OpenLibraryWorkJson withKey(final String workKey) {
        json.put("key", "/works/" + workKey);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
