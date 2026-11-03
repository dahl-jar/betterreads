package com.betterreads.mailoutbox;

import com.betterreads.clients.mail.MailProviderProperties;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
class SignupAttemptTemplate implements MailTemplate {

    private static final String FORGOT_PASSWORD_PATH = "/forgot-password";

    private static final String HEADING = "Someone tried to sign up with your email";

    private final MailProviderProperties properties;

    SignupAttemptTemplate(final MailProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return MailOutboxService.TEMPLATE_SIGNUP_ATTEMPT;
    }

    @Override
    public String subject() {
        return HEADING;
    }

    @Override
    public MailLayout.MailContent content(final String payload) {
        return new MailLayout.MailContent(
            HEADING,
            List.of(
                "Someone used this email address to sign up for BetterReads, and you already have an account.",
                "If this was you, log in or reset your password. If it wasn't, you can ignore this email."),
            "Reset password",
            properties.requireAppBaseUrl() + FORGOT_PASSWORD_PATH,
            "Your account and password stay unchanged.");
    }
}
