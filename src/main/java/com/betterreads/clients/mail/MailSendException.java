package com.betterreads.clients.mail;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

/** A failed mail send. {@code retryable} is true for network errors, 5xx and 429. */
public class MailSendException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public MailSendException(final String message, final boolean retryable, @Nullable final Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
