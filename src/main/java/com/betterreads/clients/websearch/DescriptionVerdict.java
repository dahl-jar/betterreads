package com.betterreads.clients.websearch;

record DescriptionVerdict(
    DescriptionStatus status,
    String source,
    String start,
    String end,
    String quote
) {

    static final DescriptionVerdict NONE = new DescriptionVerdict(DescriptionStatus.NOT_FOUND, "", "", "", "");
}
