package com.betterreads.catalog.service.source;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cleans a source description, judges whether it is a usable blurb, and scores the usable ones.
 *
 * <p>Rejected: a stub under the floor, a catalog-wiki dump (a heading plus a bulleted edition list),
 * and library-catalog boilerplate prefixes. A dump is detected on the raw text, since cleaning would
 * flatten the structure that marks it.
 */
public final class DescriptionQuality {

    private static final int MIN_LENGTH = 40;

    private static final int IDEAL_LENGTH = 600;

    private static final int MAX_LENGTH = 2400;

    private static final int OVERSHOOT_PENALTY = 2;

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s+\\S");

    private static final Pattern BOLD_HEADING = Pattern.compile("(?m)^\\s*\\*\\*[^*]+\\*\\*\\s*$");

    private static final Pattern BULLET_LIST_LINE = Pattern.compile("(?m)^\\s*[-*]\\s+\\S");

    private static final Pattern LINK_DEFINITION =
        Pattern.compile("(?m)^\\s*+\\[[^\\]]++]:\\s*+\\S++");

    private static final int DUMP_BULLET_THRESHOLD = 2;

    private static final List<String> BOILERPLATE_PREFIXES = List.of(
        "for use in schools and libraries only",
        "a tom doherty associates book");

    private DescriptionQuality() {
    }

    /** Cleans and assesses the raw description. */
    public static Assessment assess(final String raw) {
        final boolean dump = isDump(raw);
        final String cleaned = DescriptionCleaner.clean(raw);
        final boolean usable = !dump
            && !tooShort(cleaned)
            && cleaned.length() <= MAX_LENGTH
            && !hasBoilerplatePrefix(cleaned);
        return new Assessment(cleaned, usable, usable ? score(cleaned) : 0);
    }

    private static boolean isDump(final String raw) {
        if (HEADING.matcher(raw).find()
            || BOLD_HEADING.matcher(raw).find()
            || LINK_DEFINITION.matcher(raw).find()) {
            return true;
        }
        return BULLET_LIST_LINE.matcher(raw).results().count() >= DUMP_BULLET_THRESHOLD;
    }

    private static boolean tooShort(final String cleaned) {
        return cleaned.length() < MIN_LENGTH;
    }

    private static boolean hasBoilerplatePrefix(final String cleaned) {
        final String lower = cleaned.toLowerCase(Locale.ROOT);
        return BOILERPLATE_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    private static int score(final String cleaned) {
        final int length = cleaned.length();
        if (length <= IDEAL_LENGTH) {
            return length;
        }
        return IDEAL_LENGTH - OVERSHOOT_PENALTY * (length - IDEAL_LENGTH);
    }

    /**
     * The outcome of assessing a description.
     *
     * @param cleaned the markup-stripped text
     * @param usable true when the cleaned text is a usable blurb
     * @param score the quality score, higher is better, 0 when not usable
     */
    public record Assessment(String cleaned, boolean usable, int score) {
    }
}
