package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebSearchArgsTest {

    private static final String SCHEMA = "{\"type\":\"object\"}";

    private static final String SEARCH = "WebSearch";

    private static final WebSearchProperties PROPERTIES = WebSearchSamples.properties("claude", Duration.ofMinutes(5));

    @Test
    void shouldLockDownTools() {
        final List<String> argv = WebSearchArgs.argv(PROPERTIES, SCHEMA);

        assertThat(argv)
            .containsSequence("--tools", "WebSearch,WebFetch")
            .containsSequence("--permission-mode", "dontAsk")
            .containsSequence("--setting-sources", "");
    }

    @Test
    void shouldNotFetchSearchOnlyDomain() {
        final List<String> argv = WebSearchArgs.argv(PROPERTIES, SCHEMA);

        assertThat(argv)
            .containsSequence("--allowedTools", SEARCH, "WebFetch(domain:en.wikipedia.org)")
            .doesNotContain("WebFetch(domain:isfdb.org)");
    }

    @Test
    void shouldAskForSchemaOutput() {
        final List<String> argv = WebSearchArgs.argv(PROPERTIES, SCHEMA);

        assertThat(argv).containsSequence("--output-format", "json", "--json-schema", SCHEMA);
    }

    @Test
    void shouldHookEverySearch() {
        final List<String> argv = WebSearchArgs.argv(PROPERTIES, SCHEMA);

        final String settings = argv.get(argv.indexOf("--settings") + 1);
        assertThat(settings)
            .contains("\"matcher\":\"" + SEARCH + "\"")
            .contains("node " + WebSearchSamples.HOOK_SCRIPT);
    }
}
