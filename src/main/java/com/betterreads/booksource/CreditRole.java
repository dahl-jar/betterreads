package com.betterreads.booksource;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

public enum CreditRole {
    AUTHOR,
    EDITOR,
    ILLUSTRATOR,
    TRANSLATOR,
    NARRATOR,
    INTRODUCTION,
    OTHER;

    public static final String PRIMARY_SQL = "('AUTHOR', 'EDITOR')";

    private static final List<Terms> TERMS = List.of(
        new Terms(INTRODUCTION, List.of("introduc", "foreword", "afterword")),
        new Terms(AUTHOR, List.of("author", "writer", "compiler")),
        new Terms(EDITOR, List.of("editor")),
        new Terms(TRANSLATOR, List.of("translat")),
        new Terms(ILLUSTRATOR, List.of("illustrat", "artist", "pencil", "ink", "color", "letter", "cover")),
        new Terms(NARRATOR, List.of("narrat", "read by", "speaker")));

    public static CreditRole fromSourceText(final @Nullable String text) {
        if (text == null) {
            return AUTHOR;
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        return TERMS.stream()
            .filter(terms -> terms.words().stream().anyMatch(lower::contains))
            .map(Terms::role)
            .findFirst()
            .orElse(OTHER);
    }

    public boolean isPrimary() {
        return this == AUTHOR || this == EDITOR;
    }

    private record Terms(CreditRole role, List<String> words) {
    }
}
