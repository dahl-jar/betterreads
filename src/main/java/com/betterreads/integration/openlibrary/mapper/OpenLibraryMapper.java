package com.betterreads.integration.openlibrary.mapper;

import java.util.List;
import java.util.Map;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.CatalogGenres;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.openlibrary.dto.SearchDoc;
import com.betterreads.integration.openlibrary.dto.WorkDetail;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps OpenLibrary DTOs into the catalog's {@link SourceBook}.
 *
 * <p>{@code description} comes back as a string or a {@code {type, value}} object
 * ({@link #coerceDescription}), {@code subjects} is noisy and gets reduced to canonical genres
 * ({@link #cleanSubjects}), and {@code cover_i = 0} means no cover.
 */
@Component
public class OpenLibraryMapper {

    static final int MAX_SUBJECTS = 25;

    private static final String WORKS_PREFIX = "/works/";

    private static final String ENGLISH_LANGUAGE = "eng";

    private static final String DESCRIPTION_VALUE_KEY = "value";

    /**
     * Returns the {@link SourceBook} for an OpenLibrary work, or null if it cannot be mapped.
     *
     * <p>{@code work} is optional. The search result has the core fields; the work detail adds
     * description and subjects. Without it, those two come back null.
     */
    public @Nullable SourceBook toSourceBook(
        final @Nullable SearchDoc doc,
        final @Nullable WorkDetail work
    ) {
        if (doc == null || doc.title() == null) {
            return null;
        }
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey(stripWorksPrefix(doc.key()))
            .title(doc.title())
            .subtitle(doc.subtitle())
            .description(work == null ? null : coerceDescription(work.description()))
            .publicationYear(doc.firstPublishYear())
            .language(firstLanguage(doc.language()))
            .coverUrl(buildCoverUrl(doc.coverId()))
            .authors(SourceAuthor.ofNames(doc.authorName()))
            .rawSubjects(work == null ? null : cleanSubjects(work.subjects()))
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

    /**
     * Returns the description as plain text.
     *
     * <p>OpenLibrary sends it as a plain string on some works and a {@code {"type", "value"}}
     * object on others, which Jackson binds to a {@link String} or a {@code Map}.
     */
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
     * Returns the canonical genres across the subject strings, deduplicated and capped at
     * {@link #MAX_SUBJECTS}.
     *
     * <p>A null input returns an empty list, never null.
     */
    static List<String> cleanSubjects(final @Nullable List<String> subjects) {
        return CatalogGenres.reduceToCanonical(subjects, MAX_SUBJECTS);
    }

    /**
     * Returns the work's language, preferring English when the work has an English edition.
     *
     * <p>OpenLibrary lists every language a work has editions in, in no useful order, so a work with
     * an English edition can still list a translation first. English is the catalog's language for
     * such a work; only a work with no English edition keeps its first listed language.
     */
    private static @Nullable String firstLanguage(final @Nullable List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return languages.contains(ENGLISH_LANGUAGE) ? ENGLISH_LANGUAGE : languages.get(0);
    }
}
