package com.betterreads.catalog.service.source.quality;

import java.util.Map;

import com.betterreads.common.util.Isbn13;
import org.jspecify.annotations.Nullable;

public final class IsbnLanguage {

    private static final Map<String, String> GROUP_LANGUAGES = Map.ofEntries(
        Map.entry("9780", "en"),
        Map.entry("9781", "en"),
        Map.entry("9798", "en"),
        Map.entry("9782", "fr"),
        Map.entry("9783", "de"),
        Map.entry("9784", "ja"),
        Map.entry("9787", "zh"),
        Map.entry("97882", "no"),
        Map.entry("97884", "es"),
        Map.entry("97887", "da"),
        Map.entry("97888", "it"),
        Map.entry("97891", "sv"));

    private static final int SHORT_PREFIX = 4;

    private static final int LONG_PREFIX = 5;

    private IsbnLanguage() {
    }

    public static @Nullable String languageOf(final @Nullable String isbn13) {
        if (isbn13 == null || !Isbn13.matches(isbn13)) {
            return null;
        }
        final String shortGroupLanguage = GROUP_LANGUAGES.get(isbn13.substring(0, SHORT_PREFIX));
        return shortGroupLanguage != null
            ? shortGroupLanguage
            : GROUP_LANGUAGES.get(isbn13.substring(0, LONG_PREFIX));
    }
}
