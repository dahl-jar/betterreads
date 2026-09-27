package com.betterreads.mailoutbox;

import com.betterreads.clients.mail.MailProviderProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class MailPayloadReader {

    private final ObjectMapper objectMapper;

    private final MailProviderProperties properties;

    MailPayloadReader(final ObjectMapper objectMapper, final MailProviderProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** @throws IllegalStateException if the payload is malformed or has no token */
    String tokenLink(final String payload, final String template, final String path) {
        final String token = readToken(payload, template);
        return properties.requireAppBaseUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String readToken(final String payload, final String template) {
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
