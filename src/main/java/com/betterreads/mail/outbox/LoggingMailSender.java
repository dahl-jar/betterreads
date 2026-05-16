package com.betterreads.mail.outbox;

import com.betterreads.common.util.LogSanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link MailSender} that logs the send and discards the body.
 *
 * <p>Active when {@code mail.provider} is {@code logging} or unset.
 */
@Component
@ConditionalOnProperty(prefix = "mail", name = "provider", havingValue = "logging", matchIfMissing = true)
class LoggingMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(final MailMessage message) {
        LOG.info("Skipped real send (logging mailer) recipient={} idempotencyKey={}",
            LogSanitizer.forLog(message.recipient()), LogSanitizer.forLog(message.idempotencyKey()));
    }
}
