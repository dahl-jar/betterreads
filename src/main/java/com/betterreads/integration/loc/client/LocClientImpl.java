package com.betterreads.integration.loc.client;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.common.util.TextMatch;
import com.betterreads.integration.loc.LocClient;
import com.betterreads.integration.loc.LocSru;
import com.betterreads.integration.loc.mapper.LocMapper;
import org.springframework.stereotype.Component;

/**
 * Library of Congress SRU enrichment client.
 *
 * <p>One {@code searchRetrieve} call resolves a record; the response is MARC/MODS XML, so
 * {@link LocSru} returns the body as a string and {@link LocMapper} parses it. A title-and-author
 * lookup keeps its record only when the returned title matches the query, since the keyword index
 * can answer with a different work.
 */
@Component
public class LocClientImpl implements LocClient {

    private static final String MODS_SCHEMA = "mods";
    private static final int FIRST_RECORD = 1;
    private static final int MAX_RECORDS = 1;

    private final LocSru sru;

    private final LocMapper mapper;

    public LocClientImpl(final LocSru sru, final LocMapper mapper) {
        this.sru = sru;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.LOC;
    }

    @Override
    public Optional<SourceBook> fetchByLccn(final String lccn) {
        return search("bath.lccn=" + lccn);
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return search("bath.isbn=" + isbn);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        return search("bath.title=\"" + stripQuotes(title)
            + "\" and bath.author=\"" + stripQuotes(author) + "\"")
            .filter(book -> titleMatches(book, title));
    }

    private Optional<SourceBook> search(final String cql) {
        return sru.searchRetrieve(cql, MODS_SCHEMA, FIRST_RECORD, MAX_RECORDS)
            .flatMap(mapper::toSourceBook);
    }

    private static String stripQuotes(final String term) {
        return term.replace("\"", "");
    }

    private static boolean titleMatches(final SourceBook book, final String queryTitle) {
        final String recordTitle = book.title();
        return recordTitle != null && TextMatch.canonicalTitleMatches(recordTitle, queryTitle);
    }
}
