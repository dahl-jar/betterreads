package com.betterreads.features.bookstaging;

import com.betterreads.booksource.SourceBook;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decides whether a book carries every field needed to show it.
 *
 * <p>A Hardcover work node has no ISBN, so requiring one means a book needs Google Books or
 * OpenLibrary data before it shows.
 */
@Component
class RequiredFieldsCheck {

    private static final int MIN_DESCRIPTION_LENGTH = 20;

    private static final List<Rule> RULES = List.of(
        new Rule("title", book -> !isBlank(book.title())),
        new Rule("author", book -> book.authors() != null && !book.authors().isEmpty()),
        new Rule("cover", book -> !isBlank(book.coverUrl())),
        new Rule("description", book -> hasRealDescription(book.description())),
        new Rule("year", book -> book.publicationYear() != null),
        new Rule("isbn", book -> !isBlank(book.isbn13())));

    public MissingFields check(final SourceBook book) {
        return new MissingFields(RULES.stream()
            .filter(rule -> !rule.present().test(book))
            .map(Rule::field)
            .toList());
    }

    private static boolean isBlank(final @Nullable String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasRealDescription(final @Nullable String description) {
        return description != null && description.strip().length() >= MIN_DESCRIPTION_LENGTH;
    }

    private record Rule(String field, Predicate<SourceBook> present) {
    }

    public record MissingFields(List<String> missing) {

        public MissingFields {
            missing = List.copyOf(missing);
        }

        public boolean isReady() {
            return missing.isEmpty();
        }
    }
}
