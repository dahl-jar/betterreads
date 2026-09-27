package com.betterreads.clients.wikidata;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Resolves a display name from a Wikidata entity document.
 *
 * <p>Some entities have a null English label (Frank Herbert), so the name falls back to the English
 * Wikipedia sitelink title, then to the QID.
 */
final class WikidataLabels {

    private WikidataLabels() {
    }

    /** Returns null for an entity without an id. */
    static @Nullable String displayName(final JsonNode entity) {
        final String qid = WikidataTree.text(entity.path("id"));
        return qid == null ? null : displayName(entity, qid);
    }

    static String displayName(final JsonNode entity, final String qid) {
        return Optional.ofNullable(WikidataTree.text(entity.path("labels").path("en").path("value")))
            .or(() -> Optional.ofNullable(enwikiTitle(entity)))
            .orElse(qid);
    }

    static @Nullable String enwikiTitle(final JsonNode entity) {
        return WikidataTree.text(entity.path("sitelinks").path("enwiki").path("title"));
    }
}
