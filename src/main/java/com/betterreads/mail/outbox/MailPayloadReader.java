package com.betterreads.mail.outbox;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads an outbox row's JSON payload. */
@Component
class MailPayloadReader {

    private final ObjectMapper objectMapper;

    MailPayloadReader(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @throws IllegalStateException if the payload is malformed or carries no token */
    String readToken(final String payload, final String template) {
        try {
            final JsonNode node = objectMapper.readTree(payload);
            final String token = node.path("token").asString();
            if (token.isEmpty()) {
                throw new IllegalStateException(template + " payload missing token");
            }
            return token;
        } catch (final JacksonException ex) {
            throw new IllegalStateException("malformed " + template + " payload", ex);
        }
    }
}
