package com.betterreads.integration.openlibrary.mapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.CatalogGenres;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.openlibrary.dto.SearchDoc;
import com.betterreads.integration.openlibrary.dto.WorkDetail;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps OpenLibrary DTOs into the catalog's {@link SourceBook}.
 *
 * <p>OpenLibrary has a few quirks this handles: {@code description} comes back as a string or a
 * {@code {type, value}} object ({@link #coerceDescription}), {@code subjects} is noisy and gets
 * reduced to canonical genres ({@link #cleanSubjects}), and {@code cover_i = 0} means no cover.
 */
@Component
public class OpenLibraryMapper {

    static final int MAX_SUBJECTS = 25;

    private static final String WORKS_PREFIX = "/works/";

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
        return new SourceBook(
            BookFieldSource.OPEN_LIBRARY,
            null,
            stripWorksPrefix(doc.key()),
            null,
            null,
            null,
            null,
            doc.title(),
            doc.subtitle(),
            work == null ? null : coerceDescription(work.description()),
            doc.firstPublishYear(),
            null,
            null,
            firstLanguage(doc.language()),
            buildCoverUrl(doc.coverId()),
            doc.authorName(),
            work == null ? null : cleanSubjects(work.subjects()),
            null,
            null,
            null,
            null,
            null);
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
     * <p>OpenLibrary sends a plain string on some works and a {@code {"type", "value"}} object on
     * others, which Jackson binds to a {@link String} or a {@code Map}. Both come out as the text.
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
     * Returns the canonical genres mentioned across the subject strings, deduplicated and capped.
     *
     * <p>{@link CatalogGenres#extractGenres} does the per-subject reduction; this just collects the
     * results. A null input returns an empty list rather than null, so the caller decides what a
     * book with no fetched work detail should store.
     */
    static List<String> cleanSubjects(final @Nullable List<String> subjects) {
        if (subjects == null) {
            return List.of();
        }
        final Set<String> canonical = new LinkedHashSet<>();
        for (final String subject : subjects) {
            canonical.addAll(CatalogGenres.extractGenres(subject));
            if (canonical.size() >= MAX_SUBJECTS) {
                break;
            }
        }
        return new ArrayList<>(canonical);
    }

    private static @Nullable String firstLanguage(final @Nullable List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return languages.get(0);
    }
}
