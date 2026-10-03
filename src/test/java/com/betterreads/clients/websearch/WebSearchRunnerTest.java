package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WebSearchRunnerTest {

    private static final String SCHEMA = "{\"type\":\"object\"}";

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final String PROMPT = "Red Rising";

    private static final String HOOK = "hook.mjs";

    private static final long CHILD_EXIT_SECONDS = 5;

    private static final String SLOW_STUB = "sleep 5";

    private static final String PASSED_ENV = "PATH|HOME|USER|LANG|CLAUDE_CONFIG_DIR|CLAUDE_CODE_OAUTH_TOKEN"
        + "|WEB_SEARCH_ALLOWED_DOMAINS|PWD|SHLVL|_";

    @TempDir
    private Path dir;

    private Optional<JsonNode> runStub(final String script) throws IOException {
        return runStubForResult(script, HOOK).map(WebSearchResult::answer);
    }

    private Optional<WebSearchResult> runStubForResult(final String script, final String hookName)
        throws IOException {
        Files.writeString(dir.resolve(HOOK), "");
        final Path stub = dir.resolve("stub.sh");
        Files.writeString(stub, "#!/bin/sh\n" + script + "\n");
        Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwx------"));
        final WebSearchProperties properties =
            WebSearchSamples.properties(stub.toString(), TIMEOUT, dir.resolve(hookName).toString());
        return new WebSearchRunner(properties).run(PROMPT, SCHEMA);
    }

    private static String echoOutput(final String structuredOutput) {
        return "cat > /dev/null; echo \"{\\\"is_error\\\":false,\\\"structured_output\\\":" + structuredOutput + "}\"";
    }

    @Nested
    class Output {

        @Test
        void shouldReturnStructuredOutput() throws IOException {
            final Optional<JsonNode> output = runStub(echoOutput("{\\\"books\\\":[]}"));

            assertThat(output).get().satisfies(node -> assertThat(node.has("books")).isTrue());
        }

        @Test
        void shouldReturnTheRunUsage() throws IOException {
            final String script = "cat > /dev/null; echo '{\"is_error\":false,\"structured_output\":{},"
                + "\"num_turns\":1,\"total_cost_usd\":0.0221228,\"duration_ms\":1611,"
                + "\"permission_denials\":[{\"tool_name\":\"WebFetch\"}]}'";

            final Optional<WebSearchResult> result = runStubForResult(script, HOOK);

            assertThat(result).get()
                .isEqualTo(new WebSearchResult(new JsonMapper().createObjectNode(), WebSearchSamples.USAGE));
        }

        @Test
        void shouldSendPromptOnStdin() throws IOException {
            final Optional<JsonNode> output = runStub(
                "read -r line; echo \"{\\\"is_error\\\":false,\\\"structured_output\\\":"
                    + "{\\\"prompt\\\":\\\"$line\\\"}}\"");

            assertThat(output).get().satisfies(node -> assertThat(node.get("prompt").asString()).isEqualTo(PROMPT));
        }

        @Test
        void shouldPassAllowedDomains() throws IOException {
            final Optional<JsonNode> output =
                runStub(echoOutput("{\\\"domains\\\":\\\"$WEB_SEARCH_ALLOWED_DOMAINS\\\"}"));

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
    }

    @Nested
    class Failures {

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
            final Optional<WebSearchResult> output =
                runStubForResult(echoOutput("{}"), "missing.mjs");

            assertThat(output).isEmpty();
        }

        @Test
        void shouldReturnEmptyOnTimeout() throws IOException {
            final Optional<JsonNode> output = runStub(SLOW_STUB);

            assertThat(output).isEmpty();
        }

        @Test
        void shouldStopTheChildProcessesOnTimeout() throws IOException {
            final Path pidFile = dir.resolve("child.pid");

            runStub("sleep 30 & echo $! > " + pidFile + "; wait");

            final long childPid = Long.parseLong(Files.readString(pidFile).strip());
            ProcessHandle.of(childPid).map(ProcessHandle::onExit)
                .ifPresent(exit -> exit.completeOnTimeout(null, CHILD_EXIT_SECONDS, TimeUnit.SECONDS).join());
            assertThat(ProcessHandle.of(childPid).filter(ProcessHandle::isAlive)).isEmpty();
        }

        // PMD.DoNotUseThreads: the test sets and reads the interrupt flag the runner must restore.
        @SuppressWarnings("PMD.DoNotUseThreads")
        @Test
        void shouldKeepTheInterruptFlagWhenInterrupted() throws IOException {
            Thread.currentThread().interrupt();

            final Optional<JsonNode> output = runStub(SLOW_STUB);

            assertThat(Thread.interrupted()).isTrue();
            assertThat(output).isEmpty();
        }
    }
}
