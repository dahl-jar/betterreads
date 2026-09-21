package com.betterreads.catalog.service.source.quality;

import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class LanguageCodes {

    private static final String UNDETERMINED = "und";

    private static final Map<String, String> ISO_639_2_TO_1 = Map.ofEntries(
        Map.entry("eng", "en"),
        Map.entry("fre", "fr"),
        Map.entry("fra", "fr"),
        Map.entry("ger", "de"),
        Map.entry("deu", "de"),
        Map.entry("spa", "es"),
        Map.entry("ita", "it"),
        Map.entry("por", "pt"),
        Map.entry("dut", "nl"),
        Map.entry("nld", "nl"),
        Map.entry("swe", "sv"),
        Map.entry("nor", "no"),
        Map.entry("dan", "da"),
        Map.entry("fin", "fi"),
        Map.entry("rus", "ru"),
        Map.entry("jpn", "ja"),
        Map.entry("chi", "zh"),
        Map.entry("zho", "zh"),
        Map.entry("pol", "pl"),
        Map.entry("cze", "cs"),
        Map.entry("ces", "cs"),
        Map.entry("hun", "hu"),
        Map.entry("gre", "el"),
        Map.entry("ell", "el"),
        Map.entry("tur", "tr"),
        Map.entry("heb", "he"),
        Map.entry("ara", "ar"),
        Map.entry("kor", "ko"),
        Map.entry("lat", "la"));

    private LanguageCodes() {
    }

    public static @Nullable String iso6391(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String code = raw.strip().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        if (UNDETERMINED.equals(code)) {
            return null;
        }
        return ISO_639_2_TO_1.getOrDefault(code, code);
    }
}
