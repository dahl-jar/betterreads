package com.betterreads.security;

import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.web.RequestIdFilter;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "app.cors.allowed-origins=https://app.betterreads.example.com",
    "mail.outbox.worker-enabled=false"
})
class SecurityHeadersIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String LOGIN_URL = "/api/v1/auth/login";

    private static final String ME_URL = "/api/v1/auth/me";

    private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    private static final String CSP_HEADER = "Content-Security-Policy";

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

    private static final String SWAGGER_CSP =
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; frame-ancestors 'none'";

    private static final String SWAGGER_INDEX = "/swagger-ui/index.html";

    private static final String ALLOWED_ORIGIN = "https://app.betterreads.example.com";

    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    private static final String ORIGIN_HEADER = "Origin";

    private static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    private static final String ALLOW_METHODS_HEADER = "Access-Control-Allow-Methods";

    private static final String ALLOW_CREDENTIALS_HEADER = "Access-Control-Allow-Credentials";

    private static final String EXPOSE_HEADERS_HEADER = "Access-Control-Expose-Headers";

    private static final String REQUEST_METHOD_HEADER = "Access-Control-Request-Method";

    private static final String METHOD_POST = "POST";

    private static final String NOSNIFF_VALUE = "nosniff";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = securedMockMvc();
    }

    @Nested
    @DisplayName("Security headers")
    class SecurityHeaders {

        @Test
        void includesCoreSecurityHeadersOnUnauthorizedResponse() throws Exception {
            mockMvc.perform(get(ME_URL))
                .andExpect(header().string(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF_VALUE))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists("Permissions-Policy"));
        }

        @Test
        void apiResponseUsesStrictCsp() throws Exception {
            mockMvc.perform(get(ME_URL))
                .andExpect(header().string(CSP_HEADER, API_CSP));
        }
    }

    @Nested
    @DisplayName("Swagger UI")
    class SwaggerUi {

        @Test
        void allowsInlineScriptsAndStyles() throws Exception {
            mockMvc.perform(get(SWAGGER_INDEX))
                .andExpect(header().string(CSP_HEADER, SWAGGER_CSP));
        }
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        void shouldExposeRequestIdToAllowedOrigin() throws Exception {
            mockMvc.perform(get(ME_URL).header(ORIGIN_HEADER, ALLOWED_ORIGIN))
                .andExpect(result -> assertThat(result.getResponse().getHeader(EXPOSE_HEADERS_HEADER))
                    .contains(RequestIdFilter.HEADER));
        }

        @Test
        void shouldAllowCredentialsForAllowedOrigin() throws Exception {
            mockMvc.perform(options(LOGIN_URL)
                    .header(ORIGIN_HEADER, ALLOWED_ORIGIN)
                    .header(REQUEST_METHOD_HEADER, METHOD_POST))
                .andExpect(header().string(ALLOW_CREDENTIALS_HEADER, "true"));
        }

        @Test
        void omitsAllowOriginForDisallowedOrigin() throws Exception {
            mockMvc.perform(get(ME_URL).header(ORIGIN_HEADER, DISALLOWED_ORIGIN))
                .andExpect(header().doesNotExist(ALLOW_ORIGIN_HEADER));
        }

        @Test
        void preflightForAllowedOriginReturnsAllowMethodsIncludingPost() throws Exception {
            mockMvc.perform(options(LOGIN_URL)
                    .header(ORIGIN_HEADER, ALLOWED_ORIGIN)
                    .header(REQUEST_METHOD_HEADER, METHOD_POST))
                .andExpect(status().isOk())
                .andExpect(header().string(ALLOW_ORIGIN_HEADER, ALLOWED_ORIGIN))
                .andExpect(result -> assertThat(result.getResponse().getHeader(ALLOW_METHODS_HEADER))
                    .contains(METHOD_POST));
        }
    }
}
