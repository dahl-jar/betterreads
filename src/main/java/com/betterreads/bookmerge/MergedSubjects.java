package com.betterreads.bookmerge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;

record MergedSubjects(List<String> values, Set<BookFieldSource> sources) {

    static MergedSubjects union(final Map<BookFieldSource, SourceBook> bySource) {
        final Set<String> values = new LinkedHashSet<>();
        final Set<BookFieldSource> contributors = new LinkedHashSet<>();
        for (final SourceBook source : bySource.values()) {
            if (source.source() != BookFieldSource.STAGED) {
                add(source, values, contributors);
            }
        }
        if (values.isEmpty()) {
            final SourceBook staged = bySource.get(BookFieldSource.STAGED);
            if (staged != null) {
                add(staged, values, contributors);
            }
        }
        return new MergedSubjects(new ArrayList<>(values), contributors);
    }

    private static void add(
        final SourceBook source,
        final Set<String> values,
        final Set<BookFieldSource> contributors
    ) {
        final List<String> subjects = source.rawSubjects();
        if (subjects != null && !subjects.isEmpty()) {
            values.addAll(subjects);
            contributors.add(source.source());
        }
    }
}
