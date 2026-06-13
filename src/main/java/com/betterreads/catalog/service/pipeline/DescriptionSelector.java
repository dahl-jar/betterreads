package com.betterreads.catalog.service.pipeline;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.DescriptionQuality;
import com.betterreads.catalog.service.source.DescriptionSource;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

/**
 * Picks the best description for a merged book from the description-only sources.
 *
 * <p>Each source is cleaned and scored by {@link DescriptionQuality}, and the highest-scoring blurb
 * wins, the merge's own description included. A fallback-only source competes only when the current
 * description is unusable and no other source supplied a usable one.
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

    /** Returns the merged book carrying the best available description, with its source recorded, or unchanged. */
    public MergedBook withBestDescription(final MergedBook merged) {
        final SourceBook book = merged.book();
        return select(lookupFor(book), book.description())
            .map(winner -> merged.withDescription(
                book.toBuilder().description(winner.cleaned()).build(), winner.source()))
            .orElse(merged);
    }

    /**
     * Returns a cleaned description from the sources that beats {@code currentRaw}, the stored
     * text's own cleaned form when no source wins but cleaning changes it, or empty. The current
     * description competes, so a source only wins on a higher score.
     */
    public Optional<String> bestDescription(final DescriptionLookup lookup, final @Nullable String currentRaw) {
        final Optional<String> winner = select(lookup, currentRaw).map(Winner::cleaned);
        if (winner.isPresent() || currentRaw == null) {
            return winner;
        }
        final DescriptionQuality.Assessment current = DescriptionQuality.assess(currentRaw);
        return current.usable() && !current.cleaned().equals(currentRaw)
            ? Optional.of(current.cleaned())
            : Optional.empty();
    }

    private Optional<Winner> select(final DescriptionLookup lookup, final @Nullable String currentRaw) {
        final int currentScore = scored(currentRaw).score();
        final Optional<Winner> primary = bestAbove(currentScore, primarySources, lookup);
        if (primary.isPresent() || currentScore > UNUSABLE_SCORE) {
            return primary;
        }
        return bestAbove(currentScore, fallbackSources, lookup);
    }

    private Optional<Winner> bestAbove(
        final int floor, final List<DescriptionSource> candidates, final DescriptionLookup lookup) {
        int bestScore = floor;
        String winningText = null;
        BookFieldSource winningSource = null;

        for (final DescriptionSource source : candidates) {
            final Scored candidate = scored(fetch(source, lookup));
            if (candidate.score() > bestScore) {
                bestScore = candidate.score();
                winningText = candidate.cleaned();
                winningSource = source.source();
            }
        }

        return winningSource == null
            ? Optional.empty()
            : Optional.of(new Winner(winningText, winningSource));
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
     * Omits the OpenLibrary and Hardcover ids: the collect already fetched both records, so the
     * id-keyed sources would refetch the same data.
     */
    private static DescriptionLookup lookupFor(final SourceBook book) {
        return new DescriptionLookup(
            book.wikidataQid(), book.isbn13(), book.title(), firstAuthorName(book), null, null);
    }

    private static @Nullable String firstAuthorName(final SourceBook book) {
        final List<SourceAuthor> authors = book.authors();
        return authors == null || authors.isEmpty() ? null : authors.get(0).name();
    }

    /** Assesses a raw description once, scoring an unusable or absent one below every usable one. */
    private static Scored scored(final @Nullable String raw) {
        if (raw == null) {
            return new Scored(null, UNUSABLE_SCORE);
        }
        final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(raw);
        return assessment.usable()
            ? new Scored(assessment.cleaned(), assessment.score())
            : new Scored(null, UNUSABLE_SCORE);
    }

    private record Scored(@Nullable String cleaned, int score) {
    }

    private record Winner(@Nullable String cleaned, BookFieldSource source) {
    }
}
