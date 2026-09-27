package com.betterreads.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.testsupport.ContainerizedTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
    "cloudflare.access.aud=sun-eater-aud",
    "cloudflare.access.team-domain=hadrian.example.test",
    "mail.outbox.worker-enabled=false"
})
class ManagementSecurityIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int MANAGEMENT_PORT = TestSocketUtils.findAvailableTcpPort();

    @DynamicPropertySource
    static void managementPort(final DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @Test
    void shouldRequireAccessTokenForActuatorLinks() throws IOException, InterruptedException {
        final HttpRequest request = get("/actuator").build();

        final int status = send(request);

        assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void shouldLeaveHealthOpenWithoutAccessToken() throws IOException, InterruptedException {
        final HttpRequest request = get("/actuator/health").build();

        final int status = send(request);

        assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private static HttpRequest.Builder get(final String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + MANAGEMENT_PORT + path)).GET();
    }

    private static int send(final HttpRequest request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
