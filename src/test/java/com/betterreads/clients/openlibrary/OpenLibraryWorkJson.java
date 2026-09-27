package com.betterreads.clients.openlibrary;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ObjectNode;

final class OpenLibraryWorkJson {

    static final String HOBBIT_DESCRIPTION = "A tale of high adventure.";

    private final ObjectNode json = Fixtures.parse("""
        {\
          "key": "/works/%s",\
          "title": "%s",\
          "description": {"type": "/type/text", "value": "%s"},\
          "subjects": ["Fantasy", "thrushes", "Fantasy fiction", "the one ring", "Classics"]\
        }\
        """.formatted(OpenLibrarySearchJson.HOBBIT_WORK_KEY, OpenLibrarySearchJson.HOBBIT_TITLE,
        HOBBIT_DESCRIPTION));

    private OpenLibraryWorkJson() {
    }

    static OpenLibraryWorkJson work() {
        return new OpenLibraryWorkJson();
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
