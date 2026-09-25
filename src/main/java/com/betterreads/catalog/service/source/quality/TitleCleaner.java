package com.betterreads.catalog.service.source.quality;

import java.util.regex.Pattern;

public final class TitleCleaner {

    private static final Pattern EDITION_PARENTHETICAL =
        Pattern.compile("\\s*\\([^()]*(?:edition|book\\s+\\d+)[^()]*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EDITION_SUBTITLE =
        Pattern.compile("\\s*:\\s*[^:]*edition\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern EDITION_VARIANT_PHRASE =
        Pattern.compile("(?:the\\s+)?(?:deluxe|illustrated|annotated|collector'?s|anniversary)"
            + "\\s+edition,?\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern SPLIT_PART_SUFFIX =
        Pattern.compile(",?\\s+part\\s+(?:one|two|three|four|five|\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NOVELIZATION_LABEL =
        Pattern.compile("(?:\\s*:\\s*(?:an?\\s+|the\\s+)?(?:official\\s+)?(?:(?:movie|film)\\s+)?"
            + "|\\s+(?:official\\s+)?(?:movie|film)\\s+|\\s+official\\s+)"
            + "novelization(?:\\s+of\\s+the\\s+(?:film|movie))?\\s*$", Pattern.CASE_INSENSITIVE);

    private TitleCleaner() {
    }

    public static String clean(final String title) {
        final String withoutParenthetical = EDITION_PARENTHETICAL.matcher(title).replaceAll("");
        final String withoutSubtitle = EDITION_SUBTITLE.matcher(withoutParenthetical).replaceAll("");
        final String withoutVariant = EDITION_VARIANT_PHRASE.matcher(withoutSubtitle).replaceAll("");
        final String withoutSplitPart = SPLIT_PART_SUFFIX.matcher(withoutVariant).replaceAll("");
        return NOVELIZATION_LABEL.matcher(withoutSplitPart).replaceAll("").strip();
    }
}
