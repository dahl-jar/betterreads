package com.betterreads.integration.hardcover;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;

final class Credits {

    static final String AUTHOR = "Author";

    private Credits() {
    }

    static void add(final ArrayNode contributions, final @Nullable String role, final String name) {
        contributions.addObject().put("contribution", role).putObject("author").put("name", name);
    }
}
