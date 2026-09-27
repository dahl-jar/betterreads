package com.betterreads.clients.wikipedia;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.DescriptionSource;
import com.betterreads.clients.wikidata.WikidataApi;
import org.springframework.stereotype.Component;

/**
 * Book description from the English Wikipedia article linked to the book's Wikidata entity.
 *
 * <p>Article titles vary ("Red Rising (novel)" or just "Red Rising"), so the title comes from the
 * entity's {@code enwiki} sitelink.
 *
 * <p>Fallback only, since the article lead covers the book's publication and reception.
 */
@Component
public class WikipediaDescriptionSource implements DescriptionSource {

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
        return wikidataApi.enwikiTitle(qid).map(title -> title.replace(' ', '_'));
    }
}
