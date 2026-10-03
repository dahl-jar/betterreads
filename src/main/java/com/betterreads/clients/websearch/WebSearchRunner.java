package com.betterreads.clients.websearch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.betterreads.logging.LogSanitizer;
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

    private final WebSearchProperties properties;

    WebSearchRunner(final WebSearchProperties properties) {
        this.properties = properties;
    }

    // PMD.DoNotUseThreads: restores the interrupt flag after an interrupted wait.
    @SuppressWarnings("PMD.DoNotUseThreads")
    Optional<WebSearchResult> run(final String prompt, final String jsonSchema) {
        if (!Files.isRegularFile(Path.of(properties.hookScript()))) {
            LOG.warn("web-search hook script missing, search blocked");
            return Optional.empty();
        }
        final Path output;
        try {
            output = Files.createTempFile("web-search-", ".json");
        } catch (IOException ex) {
            LOG.warn("web-search could not create its output file ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        }
        try {
            return runInto(output, prompt, jsonSchema);
        } catch (IOException ex) {
            LOG.warn("web-search failed ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            deleteQuietly(output);
        }
    }

    private Optional<WebSearchResult> runInto(final Path output, final String prompt, final String jsonSchema)
        throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(WebSearchArgs.argv(properties, jsonSchema))
            .redirectOutput(output.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        limitEnvironment(builder.environment());
        final Process process = builder.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            LOG.warn("web-search closed its input early ({})", ex.getClass().getSimpleName());
        }
        if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            LOG.warn("web-search timed out after {}", properties.timeout());
            return Optional.empty();
        }
        if (process.exitValue() != 0) {
            LOG.warn("web-search exited with {}", process.exitValue());
            return Optional.empty();
        }
        return structuredOutput(Files.readString(output));
    }

    private void limitEnvironment(final Map<String, String> environment) {
        final Map<String, String> inherited = Map.copyOf(environment);
        environment.clear();
        INHERITED_ENV.stream()
            .filter(inherited::containsKey)
            .forEach(name -> environment.put(name, inherited.get(name)));
        environment.put(DOMAINS_ENV, String.join(",", properties.allowedDomains()));
    }

    private static Optional<WebSearchResult> structuredOutput(final String stdout) {
        try {
            final JsonNode result = JSON.readTree(stdout);
            if (result.path("is_error").asBoolean(true)) {
                LOG.warn("web-search reported an error: {}", LogSanitizer.forLog(result.path("subtype").asString("")));
                return Optional.empty();
            }
            return Optional.of(result.path("structured_output"))
                .filter(JsonNode::isObject)
                .map(answer -> new WebSearchResult(answer, new SearchUsage(
                    result.path("num_turns").asInt(0),
                    result.path("total_cost_usd").asDouble(0),
                    result.path("duration_ms").asLong(0),
                    result.path("permission_denials").size())));
        } catch (JacksonException ex) {
            LOG.warn("web-search output was not JSON");
            return Optional.empty();
        }
    }

    private static void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            LOG.debug("web-search temp file not deleted ({})", ex.getClass().getSimpleName());
        }
    }
}
