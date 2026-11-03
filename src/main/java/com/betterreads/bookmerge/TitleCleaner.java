package com.betterreads.bookmerge;

import java.util.List;
import java.util.regex.Pattern;

final class TitleCleaner {

    private static final String BYLINE_PREFIX = " by ";

    private static final int MAX_TITLE_LENGTH = 500;

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

    private static final Pattern ALTERNATIVE_TITLE =
        Pattern.compile(";(?=\\s+or\\b)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+(?=[:;])");

    private TitleCleaner() {
    }

    static String closePunctuation(final String title) {
        final String withColon = ALTERNATIVE_TITLE.matcher(title).replaceAll(":");
        return SPACE_BEFORE_PUNCTUATION.matcher(withColon).replaceAll("");
    }

    static String clean(final String title) {
        final String withoutParenthetical = EDITION_PARENTHETICAL.matcher(title).replaceAll("");
        final String withoutSubtitle = EDITION_SUBTITLE.matcher(withoutParenthetical).replaceAll("");
        final String withoutVariant = EDITION_VARIANT_PHRASE.matcher(withoutSubtitle).replaceAll("");
        final String withoutSplitPart = SPLIT_PART_SUFFIX.matcher(withoutVariant).replaceAll("");
        return NOVELIZATION_LABEL.matcher(withoutSplitPart).replaceAll("").strip();
    }

    static boolean isDisplayable(final String title, final List<String> authorNames) {
        if (title.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        final String cleaned = clean(closePunctuation(title));
        return !TitleCasing.isAllCaps(cleaned) && !hasByline(cleaned, authorNames);
    }

    private static boolean hasByline(final String title, final List<String> authorNames) {
        return authorNames.stream()
            .map(name -> BYLINE_PREFIX + name.strip())
            .anyMatch(byline -> title.regionMatches(
                true, title.length() - byline.length(), byline, 0, byline.length()));
    }
}
