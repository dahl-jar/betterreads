package com.betterreads.integration.wikidata.mapper;

import com.betterreads.catalog.service.source.SourceAuthor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Builds a {@link SourceAuthor} from a Wikidata author entity.
 *
 * <p>The photo is the P18 Commons filename served through the {@code Special:FilePath} redirect; the
 * bio link is the English Wikipedia sitelink URL.
 */
public final class WikidataAuthors {

    private static final String COMMONS_FILE_PATH =
        "https://commons.wikimedia.org/wiki/Special:FilePath/";

    private static final String PHOTO_PROPERTY = "P18";

    private WikidataAuthors() {
    }

    /** Returns the author with name, QID, photo URL, and bio link resolved from the entity. */
    public static SourceAuthor fromEntity(final JsonNode entity, final String qid) {
        final String name = WikidataLabels.displayName(entity, qid);
        return new SourceAuthor(name, qid, photoUrl(entity), bioLink(entity));
    }

    private static @Nullable String photoUrl(final JsonNode entity) {
        final String file = WikidataTree.firstString(entity, PHOTO_PROPERTY);
        return file == null ? null : COMMONS_FILE_PATH + file;
    }

    private static @Nullable String bioLink(final JsonNode entity) {
        return WikidataTree.text(entity.path("sitelinks").path("enwiki").path("url"));
    }
}
