package com.betterreads.features.bookdetail;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookRepository;
import com.betterreads.ratelimit.RateLimitFilter;
import com.betterreads.sse.RecordingAccessLog;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "mail.outbox.worker-enabled=false")
class BookStreamFirstSendTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String BOOK_KEY = "largebook";

    private static final String STREAM_PATH = "/api/v1/books/" + BOOK_KEY + "/events";

    private static final int LARGER_THAN_WRITE_BUFFER = 40_000;

    private static final long WAIT_SECONDS = 15L;

    private static final String CLIENT_CLOSED = "499";

    private static final String UNEXPECTED_ERROR_LOG = "Unexpected error";

    @LocalServerPort
    private int port;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HeldRequest held;

    @Autowired
    private RecordingAccessLog accessLog;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Test
    void shouldNotLogErrorWhenClientResetsDuringFirstSend(final CapturedOutput output) throws IOException {
        rateLimitFilter.reset();
        seedLargeBook();

        resetDuringRequest();

        final String status = accessLog.loggedStatus().orTimeout(WAIT_SECONDS, TimeUnit.SECONDS).join();
        assertThat(status).isEqualTo(CLIENT_CLOSED);
        assertThat(output.getAll()).doesNotContain(UNEXPECTED_ERROR_LOG);
    }

    private void seedLargeBook() {
        transaction.executeWithoutResult(status -> {
            final Author author = authors.save(Books.author("Author"));
            books.save(Books.promoted(BOOK_KEY, "Title", author));
        });
        jdbc.update("UPDATE book SET description = ? WHERE dedup_key = ?",
            "d".repeat(LARGER_THAN_WRITE_BUFFER), BOOK_KEY);
    }

    private void resetDuringRequest() throws IOException {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", port))) {
            final String request = "GET " + STREAM_PATH + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
            channel.write(StandardCharsets.US_ASCII.encode(request));
            held.arrived.orTimeout(WAIT_SECONDS, TimeUnit.SECONDS).join();
            channel.setOption(StandardSocketOptions.SO_LINGER, 0);
        }
        held.clientGone.complete(null);
    }

    @TestConfiguration
    static class ClosedClientConfig {

        @Bean
        HeldRequest heldRequest() {
            return new HeldRequest();
        }

        @Bean
        RecordingAccessLog recordingAccessLog() {
            return new RecordingAccessLog();
        }

        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> accessLogCustomizer(
            final RecordingAccessLog accessLog
        ) {
            return factory -> factory.addEngineValves(accessLog);
        }
    }

    static final class HeldRequest extends OncePerRequestFilter {

        private final CompletableFuture<Void> arrived = new CompletableFuture<>();

        private final CompletableFuture<Void> clientGone = new CompletableFuture<>();

        @Override
        protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain chain
        ) throws ServletException, IOException {
            if (STREAM_PATH.equals(request.getRequestURI())) {
                arrived.complete(null);
                clientGone.orTimeout(WAIT_SECONDS, TimeUnit.SECONDS).join();
            }
            chain.doFilter(request, response);
        }
    }
}
