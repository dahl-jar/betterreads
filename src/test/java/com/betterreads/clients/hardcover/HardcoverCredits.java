package com.betterreads.clients.hardcover;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;

final class HardcoverCredits {

    static final String AUTHOR = "Author";

    private HardcoverCredits() {
    }

    static void add(final ArrayNode contributions, final @Nullable String role, final String name) {
        contributions.addObject().put("contribution", role).putObject("author").put("name", name);
    }
}
