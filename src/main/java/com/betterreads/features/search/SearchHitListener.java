package com.betterreads.features.search;

import com.betterreads.logging.LogSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

@Component
@RequiredArgsConstructor
class SearchHitListener {

    private static final Logger LOG = LoggerFactory.getLogger(SearchHitListener.class);

    private final BookSearchService searchService;

    private final SearchHitEmitters emitters;

    @EventListener
    public void onBookIndexed(final BookIndexedEvent event) {
        emitters.openQueries().forEach(queryKey -> pushHit(queryKey, event.dedupKey()));
    }

    private void pushHit(final String queryKey, final String bookId) {
        try {
            searchService.hitFor(queryKey, bookId)
                .ifPresent(hit -> emitters.publish(queryKey, hit));
        } catch (JacksonException ex) {
            LOG.warn("search.hit-check failed, could not read the index document query={} bookId={} ({})",
                LogSanitizer.forLog(queryKey), LogSanitizer.forLog(bookId), ex.getClass().getSimpleName());
        } catch (IllegalStateException ex) {
            LOG.warn("search.hit-push failed, could not close the failed stream query={} bookId={} ({})",
                LogSanitizer.forLog(queryKey), LogSanitizer.forLog(bookId), ex.getClass().getSimpleName());
        }
    }
}
