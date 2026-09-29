package com.betterreads.clients.websearch;

import java.util.List;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class WebSearchArgs {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String SEARCH = "WebSearch";

    private static final String HOOKS = "hooks";

    private static final String COMMAND = "command";

    private WebSearchArgs() {
    }

    static List<String> argv(final WebSearchProperties properties, final String jsonSchema) {
        return Stream.of(
                Stream.of(
                    properties.bin(), "-p",
                    "--model", properties.model(),
                    "--effort", properties.effort(),
                    "--no-session-persistence",
                    "--setting-sources", "",
                    "--settings", settings(properties.hookScript()),
                    "--tools", SEARCH + ",WebFetch",
                    "--allowedTools", SEARCH),
                properties.allowedDomains().stream().map(domain -> "WebFetch(domain:" + domain + ")"),
                Stream.of(
                    "--permission-mode", "dontAsk",
                    "--max-turns", String.valueOf(properties.maxTurns()),
                    "--output-format", "json",
                    "--json-schema", jsonSchema))
            .flatMap(args -> args)
            .toList();
    }

    private static String settings(final String hookScript) {
        final ObjectNode settings = JSON.createObjectNode();
        final ObjectNode hook = settings.putObject(HOOKS).putArray("PreToolUse").addObject().put("matcher", SEARCH);
        hook.putArray(HOOKS).addObject().put("type", COMMAND).put(COMMAND, "node " + hookScript + " || exit 2");
        return settings.toString();
    }
}
