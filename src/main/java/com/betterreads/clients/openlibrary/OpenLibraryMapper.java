package com.betterreads.clients.openlibrary;

import java.util.List;
import java.util.Map;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Maps OpenLibrary search and work records to a {@code SourceBook}. */
@Component
class OpenLibraryMapper {

    private static final String WORKS_PREFIX = "/works/";

    private static final String ENGLISH_LANGUAGE = "eng";

    private static final String DESCRIPTION_VALUE_KEY = "value";

    /** null when the doc has no title. Without {@code work}, description and subjects stay null. */
    @Nullable SourceBook toSourceBook(
        final OpenLibrarySearchDoc doc,
        final @Nullable OpenLibraryWork work
    ) {
        if (doc.title() == null) {
            return null;
        }
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey(stripWorksPrefix(doc.key()))
            .title(doc.title())
            .subtitle(doc.subtitle())
            .description(work == null ? null : coerceDescription(work.description()))
            .publicationYear(doc.firstPublishYear())
            .language(preferredLanguage(doc.language()))
            .coverUrl(buildCoverUrl(doc.coverId()))
            .authors(SourceAuthor.ofNames(doc.authorName()))
            .rawSubjects(work == null ? null : CatalogGenres.reduceToCanonical(work.subjects()))
            .build();
    }

    static @Nullable String stripWorksPrefix(final @Nullable String key) {
        if (key == null) {
            return null;
        }
        return key.startsWith(WORKS_PREFIX) ? key.substring(WORKS_PREFIX.length()) : key;
    }

    static @Nullable String buildCoverUrl(final @Nullable Integer coverId) {
        if (coverId == null || coverId == 0) {
            return null;
        }
        return String.format("https://covers.openlibrary.org/b/id/%d-L.jpg", coverId);
    }

    /** Jackson binds the description to a String or to a Map holding {@code {"type", "value"}}. */
    static @Nullable String coerceDescription(final @Nullable Object description) {
        if (description instanceof String text) {
            return text.isBlank() ? null : text;
        }
        if (description instanceof Map<?, ?> wrapped) {
            final Object value = wrapped.get(DESCRIPTION_VALUE_KEY);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * OpenLibrary lists every edition language in no useful order, so a translation can come before
     * English. English wins when listed, else the first language.
     */
    private static @Nullable String preferredLanguage(final @Nullable List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return languages.contains(ENGLISH_LANGUAGE) ? ENGLISH_LANGUAGE : languages.get(0);
    }
}
