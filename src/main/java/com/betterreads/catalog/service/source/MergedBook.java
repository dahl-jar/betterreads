package com.betterreads.catalog.service.source;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The result of merging several sources: the combined book, the source that supplied each
 * provenance field, and the set of sources that contributed subjects.
 *
 * <p>Subjects are unioned across sources rather than taken from one source, so they carry a set of
 * contributors instead of a single entry in {@code fieldSources}.
 */
public record MergedBook(
        SourceBook book,
        Map<BookField, BookFieldSource> fieldSources,
        Set<BookFieldSource> subjectSources) {

    public MergedBook {
        fieldSources = Map.copyOf(fieldSources);
        subjectSources = Set.copyOf(subjectSources);
    }

    @Override
    public Map<BookField, BookFieldSource> fieldSources() {
        final Map<BookField, BookFieldSource> copy = new EnumMap<>(BookField.class);
        copy.putAll(fieldSources);
        return copy;
    }

    @Override
    public Set<BookFieldSource> subjectSources() {
        return Set.copyOf(subjectSources);
    }

    /** Returns the source that supplied the given field, or null when no source supplied it. */
    public @Nullable BookFieldSource provenanceOf(final BookField field) {
        return fieldSources.get(field);
    }

    /** Returns a copy with the book replaced and the provenance unchanged. */
    public MergedBook withBook(final SourceBook replacement) {
        return new MergedBook(replacement, fieldSources, subjectSources);
    }
}
