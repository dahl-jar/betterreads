package com.betterreads.clients.websearch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.betterreads.logging.LogSanitizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class WebSearchRunner {

    private static final Logger LOG = LoggerFactory.getLogger(WebSearchRunner.class);

    private static final JsonMapper JSON = new JsonMapper();

    private static final String DOMAINS_ENV = "WEB_SEARCH_ALLOWED_DOMAINS";

    private static final List<String> INHERITED_ENV =
        List.of("PATH", "HOME", "USER", "LANG", "CLAUDE_CONFIG_DIR", "CLAUDE_CODE_OAUTH_TOKEN");

    private static final int STDERR_TAIL = 2000;

    private static final int UNAUTHORIZED = 401;

    private static final Pattern USAGE_LIMIT = Pattern.compile("hit your .*limit", Pattern.CASE_INSENSITIVE);

    private static final String AUTH_FAILURE = "failed to authenticate";

    private static final String TEMP_PREFIX = "web-search-";

    private static final String ANSWER = "structured_output";

    private static final String IS_ERROR = "is_error";

    private final WebSearchProperties properties;

    WebSearchRunner(final WebSearchProperties properties) {
        this.properties = properties;
    }

    // PMD.DoNotUseThreads: restores the interrupt flag after an interrupted wait.
    @SuppressWarnings("PMD.DoNotUseThreads")
    SearchAttempt run(final String prompt, final String jsonSchema, final List<String> domains) {
        if (!Files.isRegularFile(Path.of(properties.hookScript()))) {
            return failed("hook script missing", "");
        }
        final Path output;
        final Path errors;
        try {
            output = Files.createTempFile(TEMP_PREFIX, ".json");
            errors = Files.createTempFile(TEMP_PREFIX, ".err");
        } catch (IOException ex) {
            return failed("temp file not created (" + ex.getClass().getSimpleName() + ")", "");
        }
        try {
            return runInto(new RunFiles(output, errors), prompt, jsonSchema, domains);
        } catch (IOException ex) {
            return failed(ex.getClass().getSimpleName(), tail(errors));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed("interrupted", "");
        } finally {
            deleteQuietly(output);
            deleteQuietly(errors);
        }
    }

    private SearchAttempt runInto(
        final RunFiles files, final String prompt, final String jsonSchema, final List<String> domains)
        throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(WebSearchArgs.argv(properties, jsonSchema, domains))
            .redirectOutput(files.output().toFile())
            .redirectError(files.errors().toFile());
        limitEnvironment(builder.environment(), domains);
        final Process process = builder.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            LOG.warn("web-search closed its input early ({})", ex.getClass().getSimpleName());
        }
        if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            return failed("timed out after " + properties.timeout(), tail(files.errors()));
        }
        final String stderrTail = tail(files.errors());
        final @Nullable JsonNode result = parse(Files.readString(files.output()));
        final SearchAttempt attempt = classify(process.exitValue(), result, stderrTail);
        if (attempt instanceof SearchAttempt.Failed(String reason)) {
            logFailure(reason, stderrTail);
        } else if (attempt instanceof SearchAttempt.Halted(String reason)) {
            LOG.warn("web-search halted reason={}", LogSanitizer.forLog(reason));
        }
        return attempt;
    }

    static SearchAttempt classify(final int exitCode, final @Nullable JsonNode result, final String stderrTail) {
        if (result != null && exitCode == 0 && isAnswer(result)) {
            return new SearchAttempt.Answered(result.path(ANSWER), new SearchUsage(
                result.path("num_turns").asInt(0),
                result.path("total_cost_usd").asDouble(0),
                result.path("duration_ms").asLong(0),
                result.path("permission_denials").size()));
        }
        final String text = result == null ? "" : result.path("result").asString("");
        if (isHalt(text, stderrTail, result)) {
            return new SearchAttempt.Halted(text.isEmpty() ? stderrTail : text);
        }
        return new SearchAttempt.Failed(reason(exitCode, result, text));
    }

    private static boolean isAnswer(final JsonNode result) {
        return !result.path(IS_ERROR).asBoolean(true) && result.path(ANSWER).isObject();
    }

    private static String reason(final int exitCode, final @Nullable JsonNode result, final String text) {
        final String exit = "exit " + exitCode;
        return result == null
            ? exit + ", output was not JSON"
            : exit + " subtype=" + result.path("subtype").asString("")
                + " terminal=" + result.path("terminal_reason").asString("") + " result=" + text;
    }

    private static boolean isHalt(final String text, final String stderrTail, final @Nullable JsonNode result) {
        final boolean unauthorized = result != null && result.path("api_error_status").asInt(0) == UNAUTHORIZED;
        final boolean errorText = result != null && result.path(IS_ERROR).asBoolean(false)
            && (USAGE_LIMIT.matcher(text).find() || text.toLowerCase(Locale.ROOT).contains(AUTH_FAILURE));
        return unauthorized || errorText || stderrTail.toLowerCase(Locale.ROOT).contains(AUTH_FAILURE);
    }

    private static @Nullable JsonNode parse(final String stdout) {
        try {
            final JsonNode result = JSON.readTree(stdout);
            return result.isObject() ? result : null;
        } catch (JacksonException ex) {
            return null;
        }
    }

    private void limitEnvironment(final Map<String, String> environment, final List<String> domains) {
        final Map<String, String> inherited = Map.copyOf(environment);
        environment.clear();
        INHERITED_ENV.stream()
            .filter(inherited::containsKey)
            .forEach(name -> environment.put(name, inherited.get(name)));
        environment.put(DOMAINS_ENV, String.join(",", domains));
    }

    private static SearchAttempt failed(final String reason, final String stderrTail) {
        logFailure(reason, stderrTail);
        return new SearchAttempt.Failed(reason);
    }

    private static void logFailure(final String reason, final String stderrTail) {
        LOG.warn("web-search failed reason={} stderr={}", LogSanitizer.forLog(reason),
            LogSanitizer.forLog(stderrTail));
    }

    private static String tail(final Path file) {
        try {
            final String text = Files.readString(file).strip();
            return text.substring(Math.max(0, text.length() - STDERR_TAIL));
        } catch (IOException ex) {
            return "";
        }
    }

    private static void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            LOG.debug("web-search temp file not deleted ({})", ex.getClass().getSimpleName());
        }
    }

    private record RunFiles(Path output, Path errors) {
    }
}
