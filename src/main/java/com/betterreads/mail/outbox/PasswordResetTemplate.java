package com.betterreads.mail.outbox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/** Renders the {@code password_reset} mail body. */
@Component
class PasswordResetTemplate {

    static final String SUBJECT = "Reset your BetterReads password";

    private final MailProviderProperties properties;

    private final MailPayloadReader payloads;

    PasswordResetTemplate(final MailProviderProperties properties, final MailPayloadReader payloads) {
        this.properties = properties;
        this.payloads = payloads;
    }

    String renderBody(final String payload) {
        final String token = payloads.readToken(payload, MailOutboxService.TEMPLATE_PASSWORD_RESET);
        final String resetLink = properties.requireAppBaseUrl()
            + "/reset-password?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return "Someone asked to reset the password on this account.\n\n"
            + "If that was you, open this link within 15 minutes:\n"
            + resetLink + "\n\n"
            + "If it was not you, ignore this email. Your password stays unchanged.\n";
    }
}
