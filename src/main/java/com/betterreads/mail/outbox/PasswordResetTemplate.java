package com.betterreads.mail.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/** Renders the {@code password_reset} mail body. */
@Component
class PasswordResetTemplate {

    static final String SUBJECT = "Reset your BetterReads password";

    private final MailProviderProperties properties;

    private final ObjectMapper objectMapper;

    PasswordResetTemplate(final MailProviderProperties properties, final ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    String renderBody(final String payload) {
        final String token = readToken(payload);
        final String resetLink = properties.requireAppBaseUrl()
            + "/reset-password?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return "Someone asked to reset the password on this account.\n\n"
            + "If that was you, open this link within 15 minutes:\n"
            + resetLink + "\n\n"
            + "If it was not you, ignore this email. Your password stays unchanged.\n";
    }

    private String readToken(final String payload) {
        try {
            final JsonNode node = objectMapper.readTree(payload);
            final String token = node.path("token").asText();
            if (token.isEmpty()) {
                throw new IllegalStateException("password_reset payload missing token");
            }
            return token;
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("malformed password_reset payload", ex);
        }
    }
}
