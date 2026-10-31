package com.betterreads.ratelimit;

import static com.betterreads.ratelimit.RateLimitFixtures.LOGIN_BURST;
import static com.betterreads.ratelimit.RateLimitFixtures.LOGIN_URL;
import static com.betterreads.ratelimit.RateLimitFixtures.RETRY_AFTER_HEADER;
import static com.betterreads.ratelimit.RateLimitFixtures.loginPayload;
import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.testsupport.ContainerizedTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
    "app.cors.allowed-origins=" + RateLimitCorsIntegrationTest.ALLOWED_ORIGIN,
    "mail.outbox.worker-enabled=false"
})
class RateLimitCorsIntegrationTest extends ContainerizedTest {

    static final String ALLOWED_ORIGIN = "https://app.betterreads.example.com";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int MANAGEMENT_PORT = TestSocketUtils.findAvailableTcpPort();

    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    private static final String DOCS_EVENTS_URL = "/swagger-ui/x/events";

    private static final int EVENT_STREAM_BURST = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void managementPort(final DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @BeforeEach
    void setUp() {
        rateLimitFilter.reset();
    }

    @Test
    void shouldExposeRetryAfterOnRateLimitedLogin() throws IOException, InterruptedException {
        final HttpResponse<Void> response = loginOverLimit(ALLOWED_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS).orElseThrow())
            .contains(RETRY_AFTER_HEADER);
    }

    @Test
    void shouldAllowOriginOnRateLimitedLogin() throws IOException, InterruptedException {
        final HttpResponse<Void> response = loginOverLimit(ALLOWED_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .hasValue(ALLOWED_ORIGIN);
    }

    @Test
    void shouldAnswer403ToDisallowedOriginOnRateLimitedLogin() throws IOException, InterruptedException {
        final HttpResponse<Void> response = loginOverLimit(DISALLOWED_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEmpty();
    }

    @Test
    void shouldNotRateLimitDocsPathEndingInEvents() throws IOException, InterruptedException {
        final HttpRequest docsEvents = request(DOCS_EVENTS_URL).GET().build();
        final List<Integer> statuses = new ArrayList<>();

        for (int i = 0; i <= EVENT_STREAM_BURST; i++) {
            statuses.add(send(docsEvents).statusCode());
        }

        assertThat(statuses).doesNotContain(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private HttpResponse<Void> loginOverLimit(final String origin) throws IOException, InterruptedException {
        for (int i = 0; i < LOGIN_BURST; i++) {
            login(ALLOWED_ORIGIN);
        }
        return login(origin);
    }

    private HttpResponse<Void> login(final String origin) throws IOException, InterruptedException {
        return send(request(LOGIN_URL)
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(loginPayload(objectMapper)))
            .build());
    }

    private HttpRequest.Builder request(final String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private static HttpResponse<Void> send(final HttpRequest request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding());
        }
    }
}
