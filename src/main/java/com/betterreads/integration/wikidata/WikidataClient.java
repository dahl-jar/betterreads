package com.betterreads.integration.wikidata;

import com.betterreads.catalog.service.source.port.BookSourceClient;
import com.betterreads.catalog.service.source.model.SourceBook;
import java.util.Optional;

/** Wikidata client over the entity search and entity document endpoints. */
public interface WikidataClient extends BookSourceClient {

    /**
     * Returns the book for the given Wikidata QID, or empty if none matches.
     *
     * @param qid Wikidata QID (e.g. {@code Q2831})
     */
    Optional<SourceBook> fetchByQid(String qid);
}
