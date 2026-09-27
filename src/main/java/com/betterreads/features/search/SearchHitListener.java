package com.betterreads.features.search;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SearchHitListener {

    private final BookSearchService searchService;

    private final SearchHitEmitters emitters;

    @EventListener
    public void onBookIndexed(final BookIndexedEvent event) {
        emitters.openQueries().forEach(queryKey ->
            searchService.hitFor(queryKey, event.dedupKey())
                .ifPresent(hit -> emitters.publish(queryKey, hit)));
    }
}
