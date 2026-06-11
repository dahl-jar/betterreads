package com.betterreads.integration.itunes;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.DescriptionQuality;
import com.betterreads.catalog.service.source.DescriptionSource;
import com.betterreads.common.util.TextMatch;
import org.springframework.stereotype.Component;

/**
 * Resolves a book's description from the Apple Books store.
 *
 * <p>The ISBN identifies a book exactly, so its results are trusted. The title-and-author fallback
 * is a fuzzy search whose hits can be a different book, so each result's title is checked against
 * the looked-up title. The store lists several editions of the same book and ranks its own enhanced
 * editions first, so the usable description that assesses best across the matches is returned.
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
        return bestDescription(itunesApi.results(isbn).stream());
    }

    private Optional<String> byTitleAuthor(final DescriptionLookup lookup) {
        final String title = lookup.title();
        final String author = lookup.author();
        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            return Optional.empty();
        }
        return bestDescription(itunesApi.results(title + " " + author).stream()
            .filter(result -> TextMatch.canonicalTitleMatches(result.trackName(), title)));
    }

    private static Optional<String> bestDescription(final Stream<ItunesResult> results) {
        return results
            .map(result -> new Scored(result.description(), DescriptionQuality.assess(result.description())))
            .filter(scored -> scored.assessment().usable())
            .max(Comparator.comparingInt(scored -> scored.assessment().score()))
            .map(Scored::raw);
    }

    private record Scored(String raw, DescriptionQuality.Assessment assessment) {
    }
}
