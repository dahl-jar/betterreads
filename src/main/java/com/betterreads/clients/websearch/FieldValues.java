package com.betterreads.clients.websearch;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

import com.betterreads.isbn.Isbn13;
import com.betterreads.isbn.IsbnLanguage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class FieldValues {

    private static final int MAX_TITLE_LENGTH = 300;

    private static final int MAX_AUTHOR_LENGTH = 150;

    private static final int MAX_AUTHORS = 10;

    private static final int EARLIEST_YEAR = -3000;

    private FieldValues() {
    }

    static boolean parses(final CheckedField field, final JsonNode value) {
        return switch (field) {
            case TITLE -> title(value) != null;
            case AUTHORS -> authors(value) != null;
            case YEAR -> year(value) != null;
            case ISBN -> isbn(value) != null;
            case SERIES, UNIVERSE -> NumberedSeries.from(value) != null;
        };
    }

    static boolean isEmpty(final JsonNode value) {
        if (value.isObject()) {
            return value.path("name").asString("").isBlank();
        }
        return value.isMissingNode() || value.isNull()
            || value.isString() && value.asString().isBlank()
            || value.isArray() && value.isEmpty()
            || value.isNumber() && value.asDouble() == 0;
    }

    static @Nullable String title(final JsonNode value) {
        final String text = value.asString("").strip();
        return isText(text, MAX_TITLE_LENGTH) ? text : null;
    }

    static boolean isText(final String text, final int maxLength) {
        return !text.isEmpty() && text.length() <= maxLength && text.chars().noneMatch(Character::isISOControl);
    }

    static @Nullable List<String> authors(final JsonNode value) {
        final List<String> names = value.valueStream().map(name -> name.asString("").strip()).toList();
        final boolean valid = !names.isEmpty() && names.size() <= MAX_AUTHORS
            && names.stream().allMatch(name -> isText(name, MAX_AUTHOR_LENGTH));
        return valid ? names : null;
    }

    static @Nullable Integer year(final JsonNode value) {
        final int year = value.asInt(0);
        final int latest = Year.now(ZoneOffset.UTC).getValue() + 1;
        return value.isIntegralNumber() && year != 0 && year >= EARLIEST_YEAR && year <= latest ? year : null;
    }

    static @Nullable String isbn(final JsonNode value) {
        final String isbn = value.asString("").replace("-", "").strip();
        return Isbn13.isValid(isbn) && IsbnLanguage.isEnglish(isbn) ? isbn : null;
    }
}
