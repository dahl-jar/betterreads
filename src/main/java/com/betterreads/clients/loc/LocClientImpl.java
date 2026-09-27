package com.betterreads.clients.loc;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.text.TextMatch;
import org.springframework.stereotype.Component;

/**
 * Library of Congress book lookups.
 *
 * <p>The title keyword index can answer with a different work, so a title-and-author hit is kept only
 * when its title matches the query.
 */
@Component
class LocClientImpl implements LocClient {

    private final LocSru sru;

    private final LocMapper mapper;

    LocClientImpl(final LocSru sru, final LocMapper mapper) {
        this.sru = sru;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.LOC;
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
        return sru.searchRetrieve(cql).flatMap(mapper::toSourceBook);
    }

    private static String stripQuotes(final String term) {
        return term.replace("\"", "");
    }

    private static boolean titleMatches(final SourceBook book, final String queryTitle) {
        final String recordTitle = book.title();
        return recordTitle != null && TextMatch.canonicalTitleMatches(recordTitle, queryTitle);
    }
}
