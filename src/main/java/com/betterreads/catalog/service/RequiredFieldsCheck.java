package com.betterreads.catalog.service;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decides whether a book carries the fields needed to show it: a title, at least one author, a
 * cover, a description of real length, and a publication year. Rating is excluded from the bar.
 *
 * <p>A book that misses any of these stays in {@code pending_book} and never reaches {@code book}.
 */
@Component
public class RequiredFieldsCheck {

    static final int MIN_DESCRIPTION_LENGTH = 20;

    /** Returns the required fields the book is missing, empty when it is ready to show. */
    public MissingFields check(final SourceBook book) {
        final List<String> missing = new ArrayList<>();
        if (isBlank(book.title())) {
            missing.add("title");
        }
        if (book.authors() == null || book.authors().isEmpty()) {
            missing.add("author");
        }
        if (isBlank(book.coverUrl())) {
            missing.add("cover");
        }
        if (!hasRealDescription(book.description())) {
            missing.add("description");
        }
        if (book.publicationYear() == null) {
            missing.add("year");
        }
        return new MissingFields(List.copyOf(missing));
    }

    private static boolean isBlank(final @Nullable String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasRealDescription(final @Nullable String description) {
        return description != null && description.strip().length() >= MIN_DESCRIPTION_LENGTH;
    }

    /** The required fields a book is missing. */
    public record MissingFields(List<String> missing) {

        public MissingFields {
            missing = List.copyOf(missing);
        }

        /** Returns true when the book is missing no required field. */
        public boolean isReady() {
            return missing.isEmpty();
        }
    }
}
