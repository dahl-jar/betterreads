package com.betterreads.clients.wikidata;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Maps a Wikidata entity document to a source book.
 *
 * <p>Properties hold QIDs, so genre, series and award names come from a resolver that turns a
 * QID into its display name.
 */
@Component
class WikidataMapper {

    private static final String GENRE_PROPERTY = "P136";
    private static final String AWARD_PROPERTY = "P166";
    private static final String LCCN_PROPERTY = "P244";
    private static final String OPEN_LIBRARY_PROPERTY = "P648";

    /** Returns empty when the entity has no title. */
    public Optional<SourceBook> toSourceBook(
        final JsonNode entity,
        final String qid,
        final Function<String, @Nullable String> resolveLabel
    ) {
        final String title = WikidataLabels.displayName(entity);
        if (title == null) {
            return Optional.empty();
        }
        final List<String> genres = CatalogGenres.reduceToCanonical(
            WikidataClaims.resolvedNames(entity, GENRE_PROPERTY, resolveLabel));
        return Optional.of(SourceBook.builder(BookFieldSource.WIKIDATA)
            .wikidataQid(qid)
            .title(title)
            .publicationYear(WikidataClaims.publicationYear(entity))
            .openLibraryWorkKey(WikidataTree.firstString(entity, OPEN_LIBRARY_PROPERTY))
            .locLccn(WikidataTree.firstString(entity, LCCN_PROPERTY))
            .rawSubjects(genres.isEmpty() ? null : genres)
            .awards(awards(entity, resolveLabel))
            .seriesName(WikidataTree.entityIds(entity, WikidataTree.SERIES_PROPERTY).stream()
                .findFirst()
                .map(resolveLabel)
                .orElse(null))
            .seriesPosition(WikidataClaims.seriesPosition(entity))
            .build());
    }

    /** Wikidata is the only awards source, so an empty list clears stale rows. */
    private static List<String> awards(
        final JsonNode entity,
        final Function<String, @Nullable String> resolveLabel
    ) {
        return WikidataClaims.resolvedNames(entity, AWARD_PROPERTY, resolveLabel);
    }
}
