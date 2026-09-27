package com.betterreads.booksource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Merged book plus the source behind each field.
 *
 * <p>Subjects are unioned across sources, so {@code subjectSources} lists every contributor and
 * {@code fieldSources} only the first.
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

    public @Nullable BookFieldSource provenanceOf(final BookField field) {
        return fieldSources.get(field);
    }

    public boolean resolved(final BookFieldSource source) {
        return resolvedSources.contains(source);
    }

    public MergedBook withBook(final SourceBook replacement) {
        return new MergedBook(replacement, fieldSources, subjectSources, resolvedSources);
    }

    public MergedBook withDescription(final SourceBook replacement, final BookFieldSource source) {
        final Map<BookField, BookFieldSource> updated = new EnumMap<>(BookField.class);
        updated.putAll(fieldSources);
        updated.put(BookField.DESCRIPTION, source);
        return new MergedBook(replacement, updated, subjectSources, resolvedSources);
    }

    /** a source that answered with nothing adds no book, so the merge can't tell it resolved */
    public MergedBook withResolvedSources(final Set<BookFieldSource> sources) {
        return new MergedBook(book, fieldSources, subjectSources, sources);
    }
}
