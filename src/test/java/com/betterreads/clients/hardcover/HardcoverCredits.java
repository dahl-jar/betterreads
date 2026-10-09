package com.betterreads.clients.hardcover;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;

public final class HardcoverCredits {

    public static final String AUTHOR = "Author";

    public static final String ILLUSTRATOR = "Illustrator";

    private HardcoverCredits() {
    }

    static void add(final ArrayNode contributions, final @Nullable String role, final String name) {
        contributions.addObject().put("contribution", role).putObject("author").put("name", name);
    }
}
