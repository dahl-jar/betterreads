package com.betterreads.features.search;

import com.betterreads.clients.meilisearch.MeilisearchProperties;
import com.betterreads.logging.LogSanitizer;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the books index and applies its search settings at startup.
 *
 * <p>Creating an existing index and re-applying unchanged settings are no-ops, so it runs on every
 * boot. A Meilisearch outage at startup is logged and the app still boots.
 */
@Component
@RequiredArgsConstructor
class MeilisearchIndexInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchIndexInitializer.class);

    private static final String PUBLICATION_YEAR = "publicationYear";

    private static final String[] SEARCHABLE = {"title", "subtitle", "seriesName", "authors", "subjects"};

    private static final String[] SORTABLE = {"popularityScore", PUBLICATION_YEAR};

    private static final String[] FILTERABLE =
        {BookSearchDocument.PRIMARY_KEY, "language", PUBLICATION_YEAR};

    private final Client client;

    private final MeilisearchProperties props;

    @Override
    public void run(final ApplicationArguments args) {
        try {
            final Index index = createIndexIfAbsent();
            index.waitForTask(index.updateSearchableAttributesSettings(SEARCHABLE).getTaskUid());
            index.waitForTask(index.updateSortableAttributesSettings(SORTABLE).getTaskUid());
            index.waitForTask(index.updateFilterableAttributesSettings(FILTERABLE).getTaskUid());
            LOG.info("search.index ready name={}", LogSanitizer.forLog(props.indexName()));
        } catch (MeilisearchException ex) {
            LOG.warn("search.index bootstrap failed name={} ({})",
                LogSanitizer.forLog(props.indexName()), ex.getClass().getSimpleName());
        }
    }

    private Index createIndexIfAbsent() {
        final int taskUid = client.createIndex(props.indexName(), BookSearchDocument.PRIMARY_KEY).getTaskUid();
        final Index index = client.index(props.indexName());
        index.waitForTask(taskUid);
        return index;
    }
}
