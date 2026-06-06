package com.betterreads.catalog.read.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends a keepalive comment to every open detail-page stream on a fixed interval.
 *
 * <p>The 25-second interval stays under the idle-connection cutoff along the browser to Cloudflare
 * to tunnel to Traefik path, so a stream waiting silently for a slow enrichment is held open.
 */
@Component
class BookUpdateHeartbeat {

    private final BookUpdateEmitters emitters;

    BookUpdateHeartbeat(final BookUpdateEmitters emitters) {
        this.emitters = emitters;
    }

    @Scheduled(fixedDelayString = "PT25S")
    void beat() {
        emitters.heartbeat();
    }
}
