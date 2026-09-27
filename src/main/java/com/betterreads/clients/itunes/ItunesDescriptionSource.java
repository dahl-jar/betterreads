package com.betterreads.clients.itunes;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.bookdescription.DescriptionQuality;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.DescriptionSource;
import com.betterreads.text.TextMatch;
import org.springframework.stereotype.Component;

/**
 * Book descriptions from the Apple Books store.
 *
 * <p>An ISBN search is exact. The title-and-author fallback is fuzzy and can hit a different book,
 * so each result's title must match the looked-up title. The store lists several editions and puts
 * its own enhanced editions first, so the best-scoring usable description wins.
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
        return Optional.ofNullable(lookup.isbn13())
            .filter(isbn -> !isbn.isBlank())
            .flatMap(isbn -> bestDescription(itunesApi.search(isbn).stream()));
    }

    private Optional<String> byTitleAuthor(final DescriptionLookup lookup) {
        final String title = lookup.title();
        final String author = lookup.author();
        if (title == null || title.isBlank() || author == null || author.isBlank()) {
            return Optional.empty();
        }
        return bestDescription(itunesApi.search(title + " " + author).stream()
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
