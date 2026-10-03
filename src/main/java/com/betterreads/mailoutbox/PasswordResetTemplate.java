package com.betterreads.mailoutbox;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
class PasswordResetTemplate implements MailTemplate {

    private static final String RESET_PATH = "/reset-password";

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
    public MailLayout.MailContent content(final String payload) {
        return new MailLayout.MailContent(
            "Reset your password",
            List.of("Someone asked to reset the password on this account.",
                "If that was you, reset it within 15 minutes."),
            "Reset password",
            payloads.tokenLink(payload, name(), RESET_PATH),
            "If it was not you, ignore this email. Your password stays unchanged.");
    }
}
