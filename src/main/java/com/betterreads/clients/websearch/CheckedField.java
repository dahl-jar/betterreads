package com.betterreads.clients.websearch;

import java.util.Optional;

import com.betterreads.book.VerifiedField;
import org.jspecify.annotations.Nullable;

enum CheckedField {
    TITLE("title", VerifiedField.TITLE),
    AUTHORS("authors", VerifiedField.AUTHORS),
    YEAR("year", VerifiedField.YEAR),
    SERIES("series", VerifiedField.SERIES),
    UNIVERSE("universe", null),
    ISBN("isbn13", VerifiedField.ISBN);

    private final String jsonKey;

    private final @Nullable VerifiedField verifiedField;

    CheckedField(final String jsonKey, final @Nullable VerifiedField verifiedField) {
        this.jsonKey = jsonKey;
        this.verifiedField = verifiedField;
    }

    String key() {
        return jsonKey;
    }

    Optional<VerifiedField> verified() {
        return Optional.ofNullable(verifiedField);
    }
}
