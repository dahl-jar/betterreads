package com.betterreads.clients.websearch;

import java.time.Duration;
import java.util.List;

final class WebSearchSamples {

    private static final String WIKIPEDIA = "en.wikipedia.org";

    private static final String ISFDB = "isfdb.org";

    static final List<String> DOMAINS = List.of(ISFDB, WIKIPEDIA);

    static final List<String> SEARCH_ONLY_DOMAINS = List.of(ISFDB);

    private static final int MAX_TURNS = 30;

    static final String HOOK_SCRIPT = "/opt/websearch/restrict-search.mjs";

    private WebSearchSamples() {
    }

    static WebSearchProperties properties(final String bin, final Duration timeout) {
        return properties(bin, timeout, HOOK_SCRIPT);
    }

    static WebSearchProperties properties(final String bin, final Duration timeout, final String hookScript) {
        return new WebSearchProperties(
            bin, "claude-sonnet-5-5", "low", MAX_TURNS, timeout, hookScript, DOMAINS, SEARCH_ONLY_DOMAINS);
    }
}
