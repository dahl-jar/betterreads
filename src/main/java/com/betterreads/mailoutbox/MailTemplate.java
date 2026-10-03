package com.betterreads.mailoutbox;

/** Subject and content for one kind of outbox mail. */
interface MailTemplate {

    /** Must match the {@code mail_outbox.template} value the row was queued with. */
    String name();

    String subject();

    MailLayout.MailContent content(String payload);
}
