package com.betterreads.clients.websearch;

import java.time.Duration;
import java.util.List;

final class WebSearchSamples {

    static final List<String> DOMAINS = List.of("isfdb.org", "en.wikipedia.org");

    private static final int MAX_TURNS = 30;

    static final String HOOK_SCRIPT = "/opt/websearch/restrict-search.mjs";

    private WebSearchSamples() {
    }

    static WebSearchProperties properties(final String bin, final Duration timeout) {
        return properties(bin, timeout, HOOK_SCRIPT);
    }

    static WebSearchProperties properties(final String bin, final Duration timeout, final String hookScript) {
        return new WebSearchProperties(bin, "claude-sonnet-5-5", "low", MAX_TURNS, timeout, hookScript, DOMAINS);
    }
}
