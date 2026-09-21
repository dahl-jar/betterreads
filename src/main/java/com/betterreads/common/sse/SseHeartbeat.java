package com.betterreads.common.sse;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SseHeartbeat {

    private final List<HeartbeatStreams> streams;

    SseHeartbeat(final List<HeartbeatStreams> streams) {
        this.streams = List.copyOf(streams);
    }

    @Scheduled(fixedDelayString = "PT25S")
    void beat() {
        streams.forEach(HeartbeatStreams::heartbeat);
    }
}
