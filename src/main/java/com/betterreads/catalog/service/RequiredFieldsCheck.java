package com.betterreads.catalog.service;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decides whether a book carries the fields needed to show it: a title, at least one author, a
 * cover, a description of real length, a publication year, and an ISBN. Rating is excluded from the
 * bar.
 *
 * <p>A book that misses any of these stays in {@code pending_book} and never reaches {@code book}.
 * ISBN is required because a Hardcover work node carries none, so the bar forces enrichment from a
 * source that does (Google Books, OpenLibrary) before a book shows.
 */
@Component
public class RequiredFieldsCheck {

    static final int MIN_DESCRIPTION_LENGTH = 20;

    private static final List<Rule> RULES = List.of(
        new Rule("title", book -> !isBlank(book.title())),
        new Rule("author", book -> book.authors() != null && !book.authors().isEmpty()),
        new Rule("cover", book -> !isBlank(book.coverUrl())),
        new Rule("description", book -> hasRealDescription(book.description())),
        new Rule("year", book -> book.publicationYear() != null),
        new Rule("isbn", book -> !isBlank(book.isbn13())));

    /** Returns the required fields the book is missing, empty when it is ready to show. */
    public MissingFields check(final SourceBook book) {
        final List<String> missing = RULES.stream()
            .filter(rule -> !rule.present().test(book))
            .map(Rule::field)
            .toList();
        return new MissingFields(missing);
    }

    private static boolean isBlank(final @Nullable String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasRealDescription(final @Nullable String description) {
        return description != null && description.strip().length() >= MIN_DESCRIPTION_LENGTH;
    }

    private record Rule(String field, Predicate<SourceBook> present) {
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
