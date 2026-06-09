package com.betterreads.catalog.service.source;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The result of merging several sources: the combined book, the source that supplied each
 * provenance field, the set of sources that contributed subjects, and the set of sources that
 * resolved successfully on this collect.
 *
 * <p>Subjects are unioned across sources rather than taken from one source, so they carry a set of
 * contributors instead of a single entry in {@code fieldSources}.
 *
 * <p>{@code resolvedSources} separates "the source ran and returned an answer, including a clean
 * empty" from "the source failed or was not consulted". A field-clearing write depends on this: a
 * series may be cleared only when its authority actually resolved and reported no series, not when
 * the authority timed out.
 */
public record MergedBook(
        SourceBook book,
        Map<BookField, BookFieldSource> fieldSources,
        Set<BookFieldSource> subjectSources,
        Set<BookFieldSource> resolvedSources) {

    public MergedBook {
        fieldSources = Map.copyOf(fieldSources);
        subjectSources = Set.copyOf(subjectSources);
        resolvedSources = Set.copyOf(resolvedSources);
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

    @Override
    public Set<BookFieldSource> resolvedSources() {
        return Set.copyOf(resolvedSources);
    }

    /** Returns the source that supplied the given field, or null when no source supplied it. */
    public @Nullable BookFieldSource provenanceOf(final BookField field) {
        return fieldSources.get(field);
    }

    /** Returns true when {@code source} ran and returned an answer on this collect. */
    public boolean resolved(final BookFieldSource source) {
        return resolvedSources.contains(source);
    }

    /** Returns a copy with the book replaced and the provenance unchanged. */
    public MergedBook withBook(final SourceBook replacement) {
        return new MergedBook(replacement, fieldSources, subjectSources, resolvedSources);
    }

    /** Returns a copy with the book replaced and {@link BookField#DESCRIPTION} attributed to {@code source}. */
    public MergedBook withDescription(final SourceBook replacement, final BookFieldSource source) {
        final Map<BookField, BookFieldSource> updated = new EnumMap<>(BookField.class);
        updated.putAll(fieldSources);
        updated.put(BookField.DESCRIPTION, source);
        return new MergedBook(replacement, updated, subjectSources, resolvedSources);
    }

    /** Returns a copy with {@code resolvedSources} set to {@code sources}. */
    public MergedBook withResolvedSources(final Set<BookFieldSource> sources) {
        return new MergedBook(book, fieldSources, subjectSources, sources);
    }
}
