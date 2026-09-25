package com.betterreads.integration.wikipedia;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ObjectNode;

public final class PageSummaryJson {

    private final ObjectNode json = Fixtures.json("wikipedia/page-summary.json");

    private PageSummaryJson() {
    }

    public static PageSummaryJson pageSummary() {
        return new PageSummaryJson();
    }

    public PageSummaryJson asDisambiguation(final String extract) {
        json.put("type", "disambiguation").put("extract", extract);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
