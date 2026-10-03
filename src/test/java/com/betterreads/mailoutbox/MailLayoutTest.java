package com.betterreads.mailoutbox;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailLayoutTest {

    private static final String HEADING = "Reset your password";

    private static final String FIRST_PARAGRAPH = "Someone asked to reset the password on this account.";

    private static final String SECOND_PARAGRAPH = "If that was you, reset it.";

    private static final String BUTTON = "Reset password";

    private static final String LINK = "https://test.example.com/reset-password?token=abc&b=def";

    private static final String FOOTER = "If it was not you, ignore this email.";

    private static final MailLayout.MailContent CONTENT = new MailLayout.MailContent(
        HEADING, List.of(FIRST_PARAGRAPH, SECOND_PARAGRAPH), BUTTON, LINK, FOOTER);

    @Test
    void shouldEscapeTheLinkInTheHtmlBody() {
        final String html = MailLayout.html(CONTENT);

        assertThat(html)
            .contains("token=abc&amp;b=def")
            .doesNotContain("&b=");
    }

    @Test
    void shouldPutEveryPartOfTheContentInTheHtmlBody() {
        final String html = MailLayout.html(CONTENT);

        assertThat(html).contains(HEADING, FIRST_PARAGRAPH, SECOND_PARAGRAPH, BUTTON, FOOTER);
    }

    @Test
    void shouldWriteThePlainTextBodyFromTheSameContent() {
        final String text = MailLayout.text(CONTENT);

        assertThat(text).isEqualTo("""
            Someone asked to reset the password on this account.

            If that was you, reset it.

            Reset password: https://test.example.com/reset-password?token=abc&b=def

            If it was not you, ignore this email.
            """);
    }
}
