package com.betterreads.integration.wikidata;

import com.betterreads.catalog.service.source.BookSourceClient;
import com.betterreads.catalog.service.source.SourceBook;
import java.util.Optional;

/** Wikidata client over the SPARQL endpoint and the REST entity API. */
public interface WikidataClient extends BookSourceClient {

    /**
     * Returns the book for the given Wikidata QID, or empty if none matches.
     *
     * @param qid Wikidata QID (e.g. {@code Q2831})
     */
    Optional<SourceBook> fetchByQid(String qid);
}
