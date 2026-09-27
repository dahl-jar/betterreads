package com.betterreads.clients.wikidata;

import com.betterreads.booksource.SourceAuthor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Builds a book author from a Wikidata author entity.
 *
 * <p>P18 holds only a Commons filename, so the photo URL goes through the {@code Special:FilePath}
 * redirect.
 */
final class WikidataAuthors {

    private static final String COMMONS_FILE_PATH =
        "https://commons.wikimedia.org/wiki/Special:FilePath/";

    private static final String PHOTO_PROPERTY = "P18";

    private WikidataAuthors() {
    }

    static SourceAuthor fromEntity(final JsonNode entity, final String qid) {
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
