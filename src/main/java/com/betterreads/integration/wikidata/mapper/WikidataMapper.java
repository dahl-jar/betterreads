package com.betterreads.integration.wikidata.mapper;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.CatalogGenres;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Maps a Wikidata entity document onto a {@link SourceBook}.
 *
 * <p>Wikidata properties hold QIDs, so genre, series, author, and award names come from a resolver
 * that turns a referenced QID into its display name.
 */
@Component
public class WikidataMapper {

    private static final int MAX_GENRES = 25;

    private static final String AUTHOR_PROPERTY = "P50";
    private static final String GENRE_PROPERTY = "P136";
    private static final String AWARD_PROPERTY = "P166";
    private static final String SERIES_PROPERTY = "P179";
    private static final String LCCN_PROPERTY = "P244";
    private static final String OPEN_LIBRARY_PROPERTY = "P648";

    /**
     * Returns the entity mapped to a {@link SourceBook}, or empty when it has no title.
     *
     * @param resolveLabel turns a referenced QID into its display name
     */
    public Optional<SourceBook> toSourceBook(
        final JsonNode entity,
        final String qid,
        final Function<String, @Nullable String> resolveLabel
    ) {
        final String title = WikidataClaims.label(entity);
        if (title == null) {
            return Optional.empty();
        }
        final List<String> genres = CatalogGenres.reduceToCanonical(
            WikidataClaims.resolvedNames(entity, GENRE_PROPERTY, resolveLabel), MAX_GENRES);
        final String seriesQid = WikidataTree.firstEntityId(entity, SERIES_PROPERTY);
        return Optional.of(SourceBook.builder(BookFieldSource.WIKIDATA)
            .wikidataQid(qid)
            .title(title)
            .publicationYear(WikidataClaims.publicationYear(entity))
            .openLibraryWorkKey(WikidataTree.firstString(entity, OPEN_LIBRARY_PROPERTY))
            .locLccn(WikidataTree.firstString(entity, LCCN_PROPERTY))
            .authors(authors(entity, resolveLabel))
            .rawSubjects(genres.isEmpty() ? null : genres)
            .awards(awards(entity, resolveLabel))
            .seriesName(seriesQid == null ? null : resolveLabel.apply(seriesQid))
            .seriesPosition(WikidataClaims.seriesPosition(entity))
            .build());
    }

    private static @Nullable List<SourceAuthor> authors(
        final JsonNode entity,
        final Function<String, @Nullable String> resolveLabel
    ) {
        final List<SourceAuthor> authors = WikidataTree.entityIds(entity, AUTHOR_PROPERTY).stream()
            .map(authorQid -> author(authorQid, resolveLabel))
            .filter(author -> author != null)
            .toList();
        return authors.isEmpty() ? null : authors;
    }

    private static @Nullable SourceAuthor author(
        final String authorQid,
        final Function<String, @Nullable String> resolveLabel
    ) {
        final String name = resolveLabel.apply(authorQid);
        return name == null ? null : new SourceAuthor(name, authorQid, null, null);
    }

    /**
     * Returns the awards, empty when the work has none. Wikidata is the only awards source, so an
     * empty list clears stale rows.
     */
    private static List<String> awards(
        final JsonNode entity,
        final Function<String, @Nullable String> resolveLabel
    ) {
        return WikidataTree.entityIds(entity, AWARD_PROPERTY).stream()
            .map(resolveLabel)
            .filter(name -> name != null)
            .toList();
    }
}
