package com.betterreads.features.search;

import java.util.function.Consumer;

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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the books and authors indexes and applies their search settings at startup.
 *
 * <p>Creating an existing index and re-applying unchanged settings are no-ops, so it runs on every
 * boot. A Meilisearch outage at startup is logged and the app still boots.
 */
@Component
@Order(SearchStartup.INDEX_SETTINGS)
@RequiredArgsConstructor
class MeilisearchIndexInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MeilisearchIndexInitializer.class);

    private static final String PUBLICATION_YEAR = "publicationYear";

    private static final String POPULARITY_SCORE = "popularityScore";

    private static final String[] SEARCHABLE =
        {"title", "subtitle", "seriesName", "series.name", "authors", "subjects"};

    private static final String[] SORTABLE = {POPULARITY_SCORE, PUBLICATION_YEAR};

    private static final String[] FILTERABLE =
        {BookSearchDocument.PRIMARY_KEY, "language", PUBLICATION_YEAR};

    private static final String[] AUTHOR_SEARCHABLE = {"surname", "name", "aliases", "topTitles"};

    private static final String[] AUTHOR_SORTABLE = {POPULARITY_SCORE};

    private static final String[] AUTHOR_FILTERABLE = {AuthorSearchDocument.PRIMARY_KEY};

    private static final String[] AUTHOR_RANKING_RULES =
        {"words", "typo", "proximity", "attribute", "exactness", POPULARITY_SCORE + ":desc"};

    private final Client client;

    private final MeilisearchProperties props;

    @Override
    public void run(final ApplicationArguments args) {
        bootstrap(props.indexName(), BookSearchDocument.PRIMARY_KEY, index -> {
            index.waitForTask(index.updateSearchableAttributesSettings(SEARCHABLE).getTaskUid());
            index.waitForTask(index.updateSortableAttributesSettings(SORTABLE).getTaskUid());
            index.waitForTask(index.updateFilterableAttributesSettings(FILTERABLE).getTaskUid());
        });
        bootstrap(props.authorsIndexName(), AuthorSearchDocument.PRIMARY_KEY, index -> {
            index.waitForTask(index.updateSearchableAttributesSettings(AUTHOR_SEARCHABLE).getTaskUid());
            index.waitForTask(index.updateSortableAttributesSettings(AUTHOR_SORTABLE).getTaskUid());
            index.waitForTask(index.updateFilterableAttributesSettings(AUTHOR_FILTERABLE).getTaskUid());
            index.waitForTask(index.updateRankingRulesSettings(AUTHOR_RANKING_RULES).getTaskUid());
        });
    }

    private void bootstrap(final String name, final String primaryKey, final Consumer<Index> settings) {
        try {
            final int taskUid = client.createIndex(name, primaryKey).getTaskUid();
            final Index index = client.index(name);
            index.waitForTask(taskUid);
            settings.accept(index);
            LOG.info("search.index ready name={}", LogSanitizer.forLog(name));
        } catch (MeilisearchException ex) {
            LOG.warn("search.index bootstrap failed name={} ({})",
                LogSanitizer.forLog(name), ex.getClass().getSimpleName());
        }
    }
}
