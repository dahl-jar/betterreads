package com.betterreads.search.sse;

import com.betterreads.common.sse.HeartbeatStreams;
import com.betterreads.common.sse.SseStreams;
import com.betterreads.common.util.SearchQueryKey;
import com.betterreads.search.dto.BookSearchDocument;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SearchHitEmitters implements HeartbeatStreams {

    private static final String EVENT_NAME = "search-hit";

    private final SseStreams streams = new SseStreams();

    public SseEmitter open(final String query) {
        if (streams.atCapacity()) {
            final SseEmitter emitter = streams.newEmitter();
            emitter.complete();
            return emitter;
        }
        return streams.register(SearchQueryKey.of(query));
    }

    public Set<String> openQueries() {
        return streams.openKeys();
    }

    public void publish(final String queryKey, final BookSearchDocument hit) {
        streams.send(queryKey, EVENT_NAME, hit);
    }

    @Override
    public void heartbeat() {
        streams.heartbeat();
    }
}
