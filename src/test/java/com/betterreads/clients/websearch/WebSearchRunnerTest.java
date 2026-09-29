package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class WebSearchRunnerTest {

    private static final String SCHEMA = "{\"type\":\"object\"}";

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final String PROMPT = "Darrow of Lykos";

    private static final String PASSED_ENV = "PATH|HOME|USER|LANG|CLAUDE_CONFIG_DIR|CLAUDE_CODE_OAUTH_TOKEN"
        + "|WEB_SEARCH_ALLOWED_DOMAINS|PWD|SHLVL|_";

    @TempDir
    private Path dir;

    private Optional<JsonNode> runStub(final String script) throws IOException {
        final Path hook = Files.writeString(dir.resolve("hook.mjs"), "");
        return runStub(script, hook.toString());
    }

    private Optional<JsonNode> runStub(final String script, final String hookScript) throws IOException {
        final Path stub = dir.resolve("stub.sh");
        Files.writeString(stub, "#!/bin/sh\n" + script + "\n");
        Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwx------"));
        final WebSearchProperties properties = WebSearchSamples.properties(stub.toString(), TIMEOUT, hookScript);
        return new WebSearchRunner(properties).run(PROMPT, SCHEMA);
    }

    private static String echoOutput(final String structuredOutput) {
        return "cat > /dev/null; echo \"{\\\"is_error\\\":false,\\\"structured_output\\\":" + structuredOutput + "}\"";
    }

    @Test
    void shouldReturnStructuredOutput() throws IOException {
        final Optional<JsonNode> output = runStub(echoOutput("{\\\"books\\\":[]}"));

        assertThat(output).get().satisfies(node -> assertThat(node.has("books")).isTrue());
    }

    @Test
    void shouldSendPromptOnStdin() throws IOException {
        final Optional<JsonNode> output = runStub(
            "read -r line; echo \"{\\\"is_error\\\":false,\\\"structured_output\\\":{\\\"prompt\\\":\\\"$line\\\"}}\"");

        assertThat(output).get().satisfies(node -> assertThat(node.get("prompt").asString()).isEqualTo(PROMPT));
    }

    @Test
    void shouldPassAllowedDomains() throws IOException {
        final Optional<JsonNode> output = runStub(echoOutput("{\\\"domains\\\":\\\"$WEB_SEARCH_ALLOWED_DOMAINS\\\"}"));

        assertThat(output).get()
            .satisfies(node -> assertThat(node.get("domains").asString()).isEqualTo("isfdb.org,en.wikipedia.org"));
    }

    @Test
    void shouldHideOtherEnvironment() throws IOException {
        final Optional<JsonNode> output =
            runStub(echoOutput("{\\\"env\\\":\\\"$(env | cut -d= -f1 | tr '\\n' ' ')\\\"}"));

        assertThat(output).get().satisfies(node -> assertThat(Arrays.asList(node.get("env").asString().split(" ")))
            .allMatch(name -> name.isEmpty() || name.matches(PASSED_ENV)));
    }

    @Test
    void shouldPassPath() throws IOException {
        final Optional<JsonNode> output = runStub(echoOutput("{\\\"path\\\":\\\"$PATH\\\"}"));

        assertThat(output).get()
            .satisfies(node -> assertThat(node.get("path").asString()).isEqualTo(System.getenv("PATH")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "cat > /dev/null; echo '{\"is_error\":false,\"structured_output\":{}}'; exit 1",
        "cat > /dev/null; echo '{\"is_error\":true,\"structured_output\":{}}'",
        "cat > /dev/null; echo '{\"is_error\":false}'",
        "cat > /dev/null; echo oops"})
    void shouldReturnEmptyOnBadRun(final String script) throws IOException {
        final Optional<JsonNode> output = runStub(script);

        assertThat(output).isEmpty();
    }

    @Test
    void shouldReturnEmptyWithoutHook() throws IOException {
        final Optional<JsonNode> output = runStub(echoOutput("{}"), dir.resolve("missing.mjs").toString());

        assertThat(output).isEmpty();
    }

    @Test
    void shouldReturnEmptyOnTimeout() throws IOException {
        final Optional<JsonNode> output = runStub("sleep 5");

        assertThat(output).isEmpty();
    }
}
