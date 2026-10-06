package com.betterreads.book;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;

final class BookSnapshot {

    private BookSnapshot() {
    }

    static Map<VerifiedField, @Nullable String> of(final Book book) {
        final Integer year = book.getFirstPublishYear();
        final Map<VerifiedField, @Nullable String> fields = new EnumMap<>(VerifiedField.class);
        fields.put(VerifiedField.TITLE, book.getTitle());
        fields.put(VerifiedField.AUTHORS, String.join(", ", Author.names(book.getAuthors())));
        fields.put(VerifiedField.YEAR, year == null ? null : year.toString());
        fields.put(VerifiedField.SERIES, book.getSeries().stream().map(SeriesEntry::label)
            .collect(Collectors.joining(", ")));
        fields.put(VerifiedField.DESCRIPTION, book.getDescription());
        fields.put(VerifiedField.ISBN, book.getIsbn());
        return fields;
    }
}
