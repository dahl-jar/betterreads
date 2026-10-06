package com.betterreads.clients.websearch;

import java.time.Duration;
import java.util.List;
import java.util.Map;

final class WebSearchSamples {

    private static final String WIKIPEDIA = "en.wikipedia.org";

    private static final String ISFDB = "isfdb.org";

    static final List<String> DOMAINS = List.of(ISFDB, WIKIPEDIA);

    static final List<String> SEARCH_ONLY_DOMAINS = List.of(ISFDB);

    private static final int MAX_TURNS = 30;

    private static final String MODEL = "claude-sonnet-5-5";

    private static final String EFFORT = "low";

    static final int PAGE_TIMEOUT_MS = 5000;

    private static final int PAGE_MAX_BYTES = 2 * 1024 * 1024;

    static final String HOOK_SCRIPT = "/opt/websearch/restrict-search.mjs";

    static final SearchUsage USAGE = new SearchUsage(1, 0.022_122_8, 1611, 1);

    private WebSearchSamples() {
    }

    static WebSearchProperties properties(final String bin, final Duration timeout) {
        return properties(bin, timeout, HOOK_SCRIPT);
    }

    static WebSearchProperties properties(final String bin, final Duration timeout, final String hookScript) {
        return new WebSearchProperties(bin, MODEL, EFFORT, MAX_TURNS, timeout, hookScript,
            Map.of(SourceGroup.SFF, List.of(ISFDB)), List.of(WIKIPEDIA), SEARCH_ONLY_DOMAINS,
            PAGE_TIMEOUT_MS, PAGE_TIMEOUT_MS, PAGE_MAX_BYTES, List.of("https"));
    }

    static WebSearchProperties pages(
        final String host, final int maxBytes, final List<String> schemes, final int timeout) {
        return new WebSearchProperties("claude", MODEL, EFFORT, MAX_TURNS, Duration.ofMinutes(1),
            HOOK_SCRIPT, Map.of(SourceGroup.SFF, List.of(ISFDB)), List.of(host), List.of(),
            PAGE_TIMEOUT_MS, timeout, maxBytes, schemes);
    }
}
