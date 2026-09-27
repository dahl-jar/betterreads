package com.betterreads.mailoutbox;

import org.springframework.stereotype.Component;

@Component
class EmailVerificationTemplate implements MailTemplate {

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
    public String renderBody(final String payload) {
        final String verifyLink = payloads.tokenLink(payload, name(), "/verify-email");
        return "Welcome to BetterReads.\n\n"
            + "Confirm this email address by opening the link below within 24 hours:\n"
            + verifyLink + "\n\n"
            + "If you did not sign up, ignore this email and the address will stay unverified.\n";
    }
}
