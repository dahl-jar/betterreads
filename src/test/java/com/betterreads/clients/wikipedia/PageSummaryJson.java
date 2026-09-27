package com.betterreads.clients.wikipedia;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class PageSummaryJson {

    public static final String EXTRACT =
        "Dune is a 1965 epic science fiction novel by American author Frank Herbert, the first in a "
        + "long series set on the desert planet Arrakis.";

    private static final String EXTRACT_FIELD = "extract";

    private final ObjectNode json = Fixtures.parse("""
        {"type": "standard"}
        """).put(EXTRACT_FIELD, EXTRACT);

    private PageSummaryJson() {
    }

    public static PageSummaryJson pageSummary() {
        return new PageSummaryJson();
    }

    public PageSummaryJson withExtract(final String extract) {
        json.put(EXTRACT_FIELD, extract);
        return this;
    }

    public PageSummaryJson asDisambiguation(final String extract) {
        json.put("type", "disambiguation").put(EXTRACT_FIELD, extract);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
