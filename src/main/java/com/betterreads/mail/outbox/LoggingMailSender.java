package com.betterreads.mail.outbox;

import com.betterreads.common.util.LogSanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link MailSender} for environments without a real transport. Records send events as
 * {@code mail.send.skipped} log lines and discards the body. Active when {@code mail.provider}
 * is unset or set to {@code logging}.
 */
@Component
@ConditionalOnProperty(prefix = "mail", name = "provider", havingValue = "logging", matchIfMissing = true)
class LoggingMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(final MailMessage message) {
        LOG.info("mail.send.skipped recipient={} idempotencyKey={}",
            LogSanitizer.forLog(message.recipient()), LogSanitizer.forLog(message.idempotencyKey()));
    }
}
