package com.betterreads.search.service;

import com.betterreads.common.util.LogSanitizer;
import com.betterreads.search.config.MeilisearchProperties;
import com.betterreads.search.dto.BookSearchDocument;
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
 * Creates the books index with {@code bookId} as primary key and applies its search settings at
 * startup.
 *
 * <p>Idempotent: creating an existing index and re-applying unchanged settings are no-ops, so this
 * is safe on every boot. A Meilisearch outage at startup is logged rather than failing the boot, so
 * the rest of the application still serves while search recovers.
 */
@Component
@RequiredArgsConstructor
public class MeilisearchIndexInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchIndexInitializer.class);

    private static final String PUBLICATION_YEAR = "publicationYear";

    private static final String[] SEARCHABLE = {"title", "subtitle", "seriesName", "authors", "subjects"};

    private static final String[] SORTABLE = {"popularityScore", PUBLICATION_YEAR};

    private static final String[] FILTERABLE = {"language", PUBLICATION_YEAR};

    private final Client client;

    private final MeilisearchProperties props;

    @Override
    public void run(final ApplicationArguments args) {
        try {
            createIndexIfAbsent();
            final Index index = client.index(props.indexName());
            index.waitForTask(index.updateSearchableAttributesSettings(SEARCHABLE).getTaskUid());
            index.waitForTask(index.updateSortableAttributesSettings(SORTABLE).getTaskUid());
            index.waitForTask(index.updateFilterableAttributesSettings(FILTERABLE).getTaskUid());
            LOG.info("search.index ready name={}", LogSanitizer.forLog(props.indexName()));
        } catch (MeilisearchException ex) {
            LOG.warn("search.index bootstrap failed name={} ({})",
                LogSanitizer.forLog(props.indexName()), ex.getClass().getSimpleName());
        }
    }

    private void createIndexIfAbsent() {
        final int taskUid = client.createIndex(props.indexName(), BookSearchDocument.PRIMARY_KEY).getTaskUid();
        client.index(props.indexName()).waitForTask(taskUid);
    }
}
