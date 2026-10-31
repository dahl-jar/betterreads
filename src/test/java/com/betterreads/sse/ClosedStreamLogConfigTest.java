package com.betterreads.sse;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ClosedStreamLogConfigTest {

    private static final int RANDOM_PORT = 0;

    private static final long LOG_WAIT_SECONDS = 10L;

    private static final String STREAM_PATH = "/";

    private static final String CLIENT_CLOSED = "499";

    private final RecordingAccessLog accessLog = new RecordingAccessLog();

    private final WebServer server = closedStreamServer();

    @BeforeEach
    void setUp() {
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldWriteClosedStreamToAccessLogAs499()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final HttpResponse<Void> response = requestStream();

        final String logged = accessLog.loggedStatus().get(LOG_WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(logged).isEqualTo(CLIENT_CLOSED);
    }

    private WebServer closedStreamServer() {
        final TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(RANDOM_PORT);
        final WebServerFactoryCustomizer<TomcatServletWebServerFactory> customizer =
            new ClosedStreamLogConfig().closedStreamCustomizer();
        customizer.customize(factory);
        factory.addEngineValves(accessLog);
        return factory.getWebServer(context -> {
            final ServletRegistration.Dynamic stream = context.addServlet("stream", new ClosedStream());
            stream.addMapping(STREAM_PATH);
        });
    }

    private HttpResponse<Void> requestStream() throws IOException, InterruptedException {
        final URI uri = URI.create("http://127.0.0.1:" + server.getPort() + STREAM_PATH);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        }
    }

    private static final class ClosedStream extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IOException("Broken pipe"));
            response.flushBuffer();
        }
    }

    private static final class RecordingAccessLog extends AbstractAccessLogValve {

        private final CompletableFuture<String> status = new CompletableFuture<>();

        RecordingAccessLog() {
            super();
            setPattern("%s");
        }

        CompletableFuture<String> loggedStatus() {
            return status;
        }

        @Override
        protected void log(final CharArrayWriter message) {
            status.complete(message.toString());
        }
    }
}
