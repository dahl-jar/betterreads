package com.betterreads.integration.itunes;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.DescriptionSource;
import com.betterreads.common.util.TextMatch;
import org.springframework.stereotype.Component;

/**
 * Resolves a book's description from the Apple Books store.
 *
 * <p>The ISBN identifies a book exactly, so its result is trusted. The title-and-author fallback is
 * a fuzzy search whose top hit can be a different book, so the result's title is checked against the
 * looked-up title before its blurb is used.
 */
@Component
public class ItunesDescriptionSource implements DescriptionSource {

    private final ItunesApi itunesApi;

    public ItunesDescriptionSource(final ItunesApi itunesApi) {
        this.itunesApi = itunesApi;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.ITUNES;
    }

    @Override
    public Optional<String> fetch(final DescriptionLookup lookup) {
        return byIsbn(lookup).or(() -> byTitleAuthor(lookup));
    }

    private Optional<String> byIsbn(final DescriptionLookup lookup) {
        final String isbn = lookup.isbn13();
        if (isbn == null || isbn.isBlank()) {
            return Optional.empty();
        }
        return itunesApi.firstResult(isbn).map(ItunesResult::description);
    }

    private Optional<String> byTitleAuthor(final DescriptionLookup lookup) {
        final String title = lookup.title();
        final String author = lookup.author();
        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            return Optional.empty();
        }
        return itunesApi.firstResult(title + " " + author)
            .filter(result -> TextMatch.canonicalTitleMatches(result.trackName(), title))
            .map(ItunesResult::description);
    }
}
