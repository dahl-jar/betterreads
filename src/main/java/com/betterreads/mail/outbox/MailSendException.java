package com.betterreads.mail.outbox;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

/**
 * Signals that a mail send failed. {@code retryable} distinguishes transient failures (network,
 * 5xx, 429) from permanent ones (400, malformed payload). The worker retries the former and
 * gives up on the latter.
 */
public class MailSendException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public MailSendException(final String message, final boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public MailSendException(final String message, final boolean retryable, @Nullable final Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
