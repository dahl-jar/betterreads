package com.betterreads.search.sse;

import com.betterreads.search.event.BookIndexedEvent;
import com.betterreads.search.service.BookSearchService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SearchHitListener {

    private final BookSearchService searchService;

    private final SearchHitEmitters emitters;

    public SearchHitListener(final BookSearchService searchService, final SearchHitEmitters emitters) {
        this.searchService = searchService;
        this.emitters = emitters;
    }

    @EventListener
    public void onBookIndexed(final BookIndexedEvent event) {
        emitters.openQueries().forEach(queryKey ->
            searchService.hitFor(queryKey, event.dedupKey())
                .ifPresent(hit -> emitters.publish(queryKey, hit)));
    }
}
