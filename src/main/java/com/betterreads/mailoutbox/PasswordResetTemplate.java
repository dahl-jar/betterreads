package com.betterreads.mailoutbox;

import org.springframework.stereotype.Component;

@Component
class PasswordResetTemplate implements MailTemplate {

    private final MailPayloadReader payloads;

    PasswordResetTemplate(final MailPayloadReader payloads) {
        this.payloads = payloads;
    }

    @Override
    public String name() {
        return MailOutboxService.TEMPLATE_PASSWORD_RESET;
    }

    @Override
    public String subject() {
        return "Reset your BetterReads password";
    }

    @Override
    public String renderBody(final String payload) {
        final String resetLink = payloads.tokenLink(payload, name(), "/reset-password");
        return "Someone asked to reset the password on this account.\n\n"
            + "If that was you, open this link within 15 minutes:\n"
            + resetLink + "\n\n"
            + "If it was not you, ignore this email. Your password stays unchanged.\n";
    }
}
