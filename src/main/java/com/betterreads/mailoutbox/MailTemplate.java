package com.betterreads.mailoutbox;

/** Subject and body for one kind of outbox mail. */
interface MailTemplate {

    /** Must match the {@code mail_outbox.template} value the row was queued with. */
    String name();

    String subject();

    String renderBody(String payload);
}
