package com.betterreads.mail.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/** Renders the {@code email_verification} mail body. */
@Component
class EmailVerificationTemplate {

    static final String SUBJECT = "Confirm your BetterReads email";

    private final MailProviderProperties properties;

    private final ObjectMapper objectMapper;

    EmailVerificationTemplate(final MailProviderProperties properties, final ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    String renderBody(final String payload) {
        final String token = readToken(payload);
        final String verifyLink = properties.requireAppBaseUrl()
            + "/verify-email?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return "Welcome to BetterReads.\n\n"
            + "Confirm this email address by opening the link below within 24 hours:\n"
            + verifyLink + "\n\n"
            + "If you did not sign up, ignore this email and the address will stay unverified.\n";
    }

    private String readToken(final String payload) {
        try {
            final JsonNode node = objectMapper.readTree(payload);
            final String token = node.path("token").asText();
            if (token.isEmpty()) {
                throw new IllegalStateException("email_verification payload missing token");
            }
            return token;
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("malformed email_verification payload", ex);
        }
    }
}
