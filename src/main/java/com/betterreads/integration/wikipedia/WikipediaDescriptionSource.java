package com.betterreads.integration.wikipedia;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.DescriptionSource;
import com.betterreads.integration.wikidata.WikidataApi;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Resolves a book's description from the English Wikipedia article for its Wikidata entity.
 *
 * <p>The Wikidata entity carries the {@code enwiki} sitelink, the title of the book's own article;
 * the article title is not guessed, since "Red Rising (novel)" is sometimes just "Red Rising". The
 * Wikipedia REST summary then supplies a neutral encyclopedic extract. A book with no QID, no
 * {@code enwiki} sitelink, or a non-standard page resolves to empty.
 *
 * <p>Fallback-only: the article lead states who wrote and published the book, not what happens in
 * it.
 */
@Component
public class WikipediaDescriptionSource implements DescriptionSource {

    private static final String ENWIKI_TITLE_POINTER = "/sitelinks/enwiki/title";

    private final WikidataApi wikidataApi;

    private final WikipediaApi wikipediaApi;

    public WikipediaDescriptionSource(final WikidataApi wikidataApi, final WikipediaApi wikipediaApi) {
        this.wikidataApi = wikidataApi;
        this.wikipediaApi = wikipediaApi;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.WIKIPEDIA;
    }

    @Override
    public boolean fallbackOnly() {
        return true;
    }

    @Override
    public Optional<String> fetch(final DescriptionLookup lookup) {
        final String qid = lookup.wikidataQid();
        if (qid == null || qid.isBlank()) {
            return Optional.empty();
        }
        return enwikiTitle(qid).flatMap(wikipediaApi::summaryExtract);
    }

    private Optional<String> enwikiTitle(final String qid) {
        return wikidataApi.entity(qid)
            .map(entity -> entity.at(ENWIKI_TITLE_POINTER))
            .filter(JsonNode::isValueNode)
            .map(JsonNode::asString)
            .map(title -> title.replace(' ', '_'));
    }
}
