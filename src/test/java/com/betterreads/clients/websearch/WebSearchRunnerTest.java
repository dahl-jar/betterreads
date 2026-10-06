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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
        return answer(runStubForResult(script, HOOK));
    }

    private static Optional<JsonNode> answer(final SearchAttempt attempt) {
        return attempt instanceof SearchAttempt.Answered answered ? Optional.of(answered.answer()) : Optional.empty();
    }

    private SearchAttempt runStubForResult(final String script, final String hookName) throws IOException {
        Files.writeString(dir.resolve(HOOK), "");
        final Path stub = dir.resolve("stub.sh");
        Files.writeString(stub, "#!/bin/sh\n" + script + "\n");
        Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwx------"));
        final WebSearchProperties properties =
            WebSearchSamples.properties(stub.toString(), TIMEOUT, dir.resolve(hookName).toString());
        return new WebSearchRunner(properties).run(PROMPT, SCHEMA, WebSearchSamples.DOMAINS);
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

            final SearchAttempt result = runStubForResult(script, HOOK);

            assertThat(result)
                .isEqualTo(new SearchAttempt.Answered(new JsonMapper().createObjectNode(), WebSearchSamples.USAGE));
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
        void shouldFailOnBadRun(final String script) throws IOException {
            final SearchAttempt attempt = runStubForResult(script, HOOK);

            assertThat(attempt).isInstanceOf(SearchAttempt.Failed.class);
        }

        @Test
        void shouldFailWithoutHook() throws IOException {
            final SearchAttempt attempt = runStubForResult(echoOutput("{}"), "missing.mjs");

            assertThat(attempt).isInstanceOf(SearchAttempt.Failed.class);
        }

        @Test
        void shouldFailOnTimeout() throws IOException {
            final SearchAttempt attempt = runStubForResult(SLOW_STUB, HOOK);

            assertThat(attempt).isInstanceOf(SearchAttempt.Failed.class);
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

            final SearchAttempt attempt = runStubForResult(SLOW_STUB, HOOK);

            assertThat(Thread.interrupted()).isTrue();
            assertThat(attempt).isEqualTo(new SearchAttempt.Failed("interrupted"));
        }

        @Test
        void shouldFailWhenTheCommandCannotStart() throws IOException {
            Files.writeString(dir.resolve(HOOK), "");
            final WebSearchProperties properties = WebSearchSamples.properties(
                dir.resolve("absent").toString(), TIMEOUT, dir.resolve(HOOK).toString());

            final SearchAttempt attempt = new WebSearchRunner(properties).run(PROMPT, SCHEMA, WebSearchSamples.DOMAINS);

            assertThat(attempt).isEqualTo(new SearchAttempt.Failed("IOException"));
        }

        @Test
        void shouldNameTheSubtypeInTheFailureReason() {
            final JsonNode result = new JsonMapper().readTree("{\"is_error\":true,\"subtype\":\"error_max_turns\"}");

            final SearchAttempt attempt = WebSearchRunner.classify(1, result, "");

            assertThat(attempt).isInstanceOfSatisfying(SearchAttempt.Failed.class,
                failed -> assertThat(failed.reason()).contains("exit 1 subtype=error_max_turns"));
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class Halts {

        @Test
        void shouldHaltOnAUsageLimitReportedAsSuccess() throws IOException {
            final String script = "cat > /dev/null; echo '{\"type\":\"result\",\"subtype\":\"success\","
                + "\"is_error\":true,\"result\":\"You have hit your session limit, resets 3:45pm\"}'";

            final SearchAttempt attempt = runStubForResult(script, HOOK);

            assertThat(attempt).isInstanceOf(SearchAttempt.Halted.class);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
            "{\"is_error\":true,\"result\":\"API Error\",\"api_error_status\":401}|",
            "{\"is_error\":true,\"result\":\"Failed to authenticate. Token expired\"}|",
            "|Failed to authenticate: OAuth token revoked"})
        void shouldHaltOnAnAuthFailure(final @Nullable String stdout, final @Nullable String stderr) {
            final JsonNode result = stdout == null ? null : new JsonMapper().readTree(stdout);

            final SearchAttempt attempt = WebSearchRunner.classify(1, result, stderr == null ? "" : stderr);

            assertThat(attempt).isInstanceOf(SearchAttempt.Halted.class);
        }

        @Test
        void shouldHaltWithTheStderrReasonWhenThereIsNoOutput() {
            final String stderr = "Failed to authenticate: OAuth token revoked";

            final SearchAttempt attempt = WebSearchRunner.classify(1, null, stderr);

            assertThat(attempt).isEqualTo(new SearchAttempt.Halted(stderr));
        }

        @Test
        void shouldFailOnlyTheBatchWhenTheModelWritesALimitPhrase() {
            final JsonNode result = new JsonMapper().readTree(
                "{\"subtype\":\"success\",\"is_error\":false,\"result\":\"I hit your daily limit of lookups\"}");

            final SearchAttempt attempt = WebSearchRunner.classify(0, result, "");

            assertThat(attempt).isInstanceOf(SearchAttempt.Failed.class);
        }

        @Test
        void shouldLogTheStderrTailOnFailure(final CapturedOutput output) throws IOException {
            runStubForResult("cat > /dev/null; echo 'node: config unreadable' >&2; exit 3", HOOK);

            assertThat(output.getOut()).contains("node: config unreadable");
        }
    }
}
