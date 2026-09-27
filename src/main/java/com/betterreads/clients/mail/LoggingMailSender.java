package com.betterreads.clients.mail;

import com.betterreads.logging.LogSanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Logs the recipient and drops the mail, so local runs work without a Resend key. */
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
