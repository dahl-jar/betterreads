package com.betterreads.integration.wikidata.mapper;

import tools.jackson.databind.JsonNode;

/**
 * Resolves a display name from a Wikidata entity document.
 *
 * <p>Wikidata properties are QIDs, so a name comes from the entity's own {@code labels.en}. Some
 * entities have a null English label (Frank Herbert), so the name falls back to the English
 * Wikipedia sitelink title, then to the QID.
 */
final class WikidataLabels {

    private WikidataLabels() {
    }

    /** Returns the entity's English label, its enwiki title, or {@code qid} when neither is set. */
    static String displayName(final JsonNode entity, final String qid) {
        final String label = WikidataTree.text(entity.path("labels").path("en").path("value"));
        if (label != null) {
            return label;
        }
        final String enwiki = WikidataTree.text(entity.path("sitelinks").path("enwiki").path("title"));
        if (enwiki != null) {
            return enwiki;
        }
        return qid;
    }
}
