package com.betterreads.clients.mail;

import com.betterreads.clients.http.WebClients;
import com.betterreads.logging.LogSanitizer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Sends mail through Resend's HTTP API.
 *
 * <p>The {@code Idempotency-Key} header carries the outbox row id, so Resend drops a retry of
 * the same row within its 24-hour window. 429, 5xx and timeouts are retryable, other 4xx are not.
 */
@Component
@ConditionalOnProperty(prefix = "mail", name = "provider", havingValue = "resend")
class ResendMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(ResendMailSender.class);

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);

    private static final int TOO_MANY_REQUESTS = 429;

    private final WebClient client;

    private final String fromAddress;

    ResendMailSender(final MailProviderProperties properties) {
        this.client = WebClients.builderWithTimeouts(
                RESEND_API_URL, CONNECT_TIMEOUT_MS, (int) RESPONSE_TIMEOUT.toMillis())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.requireApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.fromAddress = properties.requireFrom();
    }

    @Override
    public void send(final MailMessage message) {
        final Map<String, Object> payload = Map.of(
            "from", fromAddress,
            "to", List.of(message.recipient()),
            "subject", message.subject(),
            "text", message.body()
        );
        try {
            client.post()
                .header(IDEMPOTENCY_HEADER, message.idempotencyKey())
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(RESPONSE_TIMEOUT);
            LOG.info("Sent mail via Resend idempotencyKey={}", LogSanitizer.forLog(message.idempotencyKey()));
        } catch (final WebClientResponseException ex) {
            throw new MailSendException(
                "Resend returned " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(),
                isRetryableStatus(ex.getStatusCode()),
                ex);
        } catch (final WebClientRequestException ex) {
            throw new MailSendException("Resend request failed: " + ex.getMessage(), true, ex);
        }
    }

    private static boolean isRetryableStatus(final HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == TOO_MANY_REQUESTS;
    }
}
