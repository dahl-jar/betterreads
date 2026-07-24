package com.betterreads.mail.outbox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/** Renders the {@code email_verification} mail body. */
@Component
class EmailVerificationTemplate {

    static final String SUBJECT = "Confirm your BetterReads email";

    private final MailProviderProperties properties;

    private final MailPayloadReader payloads;

    EmailVerificationTemplate(final MailProviderProperties properties, final MailPayloadReader payloads) {
        this.properties = properties;
        this.payloads = payloads;
    }

    String renderBody(final String payload) {
        final String token = payloads.readToken(payload, MailOutboxService.TEMPLATE_EMAIL_VERIFICATION);
        final String verifyLink = properties.requireAppBaseUrl()
            + "/verify-email?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return "Welcome to BetterReads.\n\n"
            + "Confirm this email address by opening the link below within 24 hours:\n"
            + verifyLink + "\n\n"
            + "If you did not sign up, ignore this email and the address will stay unverified.\n";
    }
}
