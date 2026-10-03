package com.betterreads.mailoutbox;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
class EmailVerificationTemplate implements MailTemplate {

    private static final String VERIFY_PATH = "/verify-email";

    private final MailPayloadReader payloads;

    EmailVerificationTemplate(final MailPayloadReader payloads) {
        this.payloads = payloads;
    }

    @Override
    public String name() {
        return MailOutboxService.TEMPLATE_EMAIL_VERIFICATION;
    }

    @Override
    public String subject() {
        return "Confirm your BetterReads email";
    }

    @Override
    public MailLayout.MailContent content(final String payload) {
        return new MailLayout.MailContent(
            "Confirm your email",
            List.of("Welcome to BetterReads.", "Confirm this email address within 24 hours."),
            "Confirm email",
            payloads.tokenLink(payload, name(), VERIFY_PATH),
            "If you did not sign up, ignore this email and the address will stay unverified.");
    }
}
