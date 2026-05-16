package com.betterreads.mail.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@link MailOutboxWorker#drain()} on a fixed interval. Disabled by
 * {@code mail.outbox.worker-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "mail.outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
class MailOutboxScheduler {

    private final MailOutboxWorker worker;

    MailOutboxScheduler(final MailOutboxWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "5000")
    public void tick() {
        worker.drain();
    }
}
