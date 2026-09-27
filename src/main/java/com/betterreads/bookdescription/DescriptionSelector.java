package com.betterreads.bookdescription;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.DescriptionSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Picks the best-scoring description for a book from the description-only sources.
 *
 * <p>The book's current description competes too, so a source only wins on a higher score. A
 * fallback-only source competes only when the current description is unusable and no other source
 * has a usable one.
 */
@Component
public class DescriptionSelector {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptionSelector.class);

    private static final int UNUSABLE_SCORE = Integer.MIN_VALUE;

    private final List<DescriptionSource> primarySources;

    private final List<DescriptionSource> fallbackSources;

    public DescriptionSelector(final List<DescriptionSource> sources) {
        this.primarySources = sources.stream().filter(source -> !source.fallbackOnly()).toList();
        this.fallbackSources = sources.stream().filter(DescriptionSource::fallbackOnly).toList();
    }

    public MergedBook withBestDescription(final MergedBook merged) {
        final SourceBook book = merged.book();
        return select(lookupFor(book), scored(book.description()).score())
            .map(winner -> merged.withDescription(
                book.toBuilder().description(winner.cleaned()).build(), winner.source()))
            .orElse(merged);
    }

    /**
     * Returns the winning source description, or the stored text's own cleaned form when no source
     * wins but cleaning changes it. Empty when the stored text is already clean, so a sweep does not
     * rewrite unchanged rows.
     */
    public Optional<String> bestDescription(final DescriptionLookup lookup, final @Nullable String currentRaw) {
        if (currentRaw == null) {
            return select(lookup, UNUSABLE_SCORE).map(Candidate::cleaned);
        }
        final DescriptionQuality.Assessment current = DescriptionQuality.assess(currentRaw);
        final Optional<String> winner = select(lookup, scored(current).score()).map(Candidate::cleaned);
        if (winner.isPresent()) {
            return winner;
        }
        return current.usable() && !current.cleaned().equals(currentRaw)
            ? Optional.of(current.cleaned())
            : Optional.empty();
    }

    private Optional<Candidate> select(final DescriptionLookup lookup, final int currentScore) {
        final Optional<Candidate> primary = bestAbove(currentScore, primarySources, lookup);
        if (primary.isPresent() || currentScore > UNUSABLE_SCORE) {
            return primary;
        }
        return bestAbove(currentScore, fallbackSources, lookup);
    }

    private Optional<Candidate> bestAbove(
        final int floor, final List<DescriptionSource> candidates, final DescriptionLookup lookup) {
        return candidates.stream()
            .map(source -> candidate(source, lookup))
            .filter(candidate -> candidate.score() > floor)
            .max(Comparator.comparingInt(Candidate::score));
    }

    private Candidate candidate(final DescriptionSource source, final DescriptionLookup lookup) {
        final Scored scored = scored(fetch(source, lookup));
        return new Candidate(scored.cleaned(), source.source(), scored.score());
    }

    private @Nullable String fetch(final DescriptionSource source, final DescriptionLookup lookup) {
        try {
            return source.fetch(lookup).orElse(null);
        } catch (WebClientException ex) {
            LOG.warn("catalog.description source {} failed ({}), skipping it",
                source.source(), ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * OpenLibrary and Hardcover records are already in the merge, so their ids stay null and those
     * sources skip a refetch.
     */
    private static DescriptionLookup lookupFor(final SourceBook book) {
        return new DescriptionLookup(
            book.wikidataQid(), book.isbn13(), book.title(), firstAuthorName(book), null, null);
    }

    private static @Nullable String firstAuthorName(final SourceBook book) {
        final List<SourceAuthor> authors = book.authors();
        return authors == null || authors.isEmpty() ? null : authors.get(0).name();
    }

    private static Scored scored(final @Nullable String raw) {
        return raw == null ? new Scored(null, UNUSABLE_SCORE) : scored(DescriptionQuality.assess(raw));
    }

    private static Scored scored(final DescriptionQuality.Assessment assessment) {
        return assessment.usable()
            ? new Scored(assessment.cleaned(), assessment.score())
            : new Scored(null, UNUSABLE_SCORE);
    }

    private record Scored(@Nullable String cleaned, int score) {
    }

    private record Candidate(@Nullable String cleaned, BookFieldSource source, int score) {
    }
}
