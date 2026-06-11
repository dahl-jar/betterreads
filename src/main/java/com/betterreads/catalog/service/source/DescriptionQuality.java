package com.betterreads.catalog.service.source;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cleans a source description, judges whether it is a usable blurb, and scores the usable ones.
 *
 * <p>The score counts only the sentences that describe the story. Sentences about the publication
 * (publisher, release dates, sequels, editions) count for nothing, so a description that is all
 * publication facts is rejected even at a healthy length. An over-long blurb is cut at the last
 * sentence end under the ceiling.
 *
 * <p>Also rejected: a stub under the floor, a catalog-wiki dump (a heading plus a bulleted edition
 * list), and library-catalog boilerplate prefixes. A dump is detected on the raw text, since
 * cleaning would flatten the structure that marks it.
 */
public final class DescriptionQuality {

    private static final int MIN_LENGTH = 40;

    private static final int IDEAL_LENGTH = 600;

    private static final int MAX_LENGTH = 2400;

    private static final int OVERSHOOT_PENALTY = 2;

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");

    private static final List<Pattern> PUBLICATION_FACTS = List.of(
        Pattern.compile("\\bpubli(?:sh(?:ed|er|ers|ing)|cation)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwritten by\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bbestselling author\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:novel|book|memoir|anthology|collection) by\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bthe (?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth"
            + "|\\d{1,2}(?:st|nd|rd|th))\\s+(?:book|novel|volume|instal?lment)\\s+in\\b",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:followed|preceded) by\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bconsists of\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bedition\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\baudiobook\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcritics\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:critical|commercial) (?:acclaim|success)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwon the\\b[^.!?]*\\baward\\b", Pattern.CASE_INSENSITIVE));

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s+\\S");

    private static final Pattern BOLD_HEADING = Pattern.compile("(?m)^\\s*\\*\\*[^*]+\\*\\*\\s*$");

    private static final Pattern BULLET_LIST_LINE = Pattern.compile("(?m)^\\s*[-*]\\s+\\S");

    private static final Pattern LINK_DEFINITION =
        Pattern.compile("(?m)^\\s*+\\[[^\\]]++]:\\s*+\\S++");

    private static final int DUMP_BULLET_THRESHOLD = 2;

    private static final List<String> BOILERPLATE_PREFIXES = List.of(
        "for use in schools and libraries only",
        "a tom doherty associates book",
        "available only on apple books");

    private DescriptionQuality() {
    }

    /** Cleans and assesses the raw description. */
    public static Assessment assess(final String raw) {
        final boolean dump = isDump(raw);
        final String cleaned = cutAtSentenceEnd(DescriptionCleaner.clean(raw));
        final int storyLength = storyLength(cleaned);
        final boolean usable = !dump
            && storyLength >= MIN_LENGTH
            && cleaned.length() <= MAX_LENGTH
            && !hasBoilerplatePrefix(cleaned);
        return new Assessment(cleaned, usable, usable ? score(storyLength) : 0);
    }

    /**
     * Returns the text cut at the last sentence end under the length ceiling, or unchanged when it
     * already fits or no sentence end leaves a usable cut.
     */
    private static String cutAtSentenceEnd(final String text) {
        if (text.length() <= MAX_LENGTH) {
            return text;
        }
        for (int i = MAX_LENGTH - 1; i >= MIN_LENGTH; i--) {
            final char character = text.charAt(i);
            if (character == '.' || character == '!' || character == '?') {
                return text.substring(0, i + 1);
            }
        }
        return text;
    }

    private static int storyLength(final String cleaned) {
        return Stream.of(SENTENCE_BREAK.split(cleaned))
            .filter(sentence -> !isPublicationFact(sentence))
            .mapToInt(String::length)
            .sum();
    }

    private static boolean isPublicationFact(final String sentence) {
        return PUBLICATION_FACTS.stream().anyMatch(fact -> fact.matcher(sentence).find());
    }

    private static boolean isDump(final String raw) {
        if (HEADING.matcher(raw).find()
            || BOLD_HEADING.matcher(raw).find()
            || LINK_DEFINITION.matcher(raw).find()) {
            return true;
        }
        return BULLET_LIST_LINE.matcher(raw).results().count() >= DUMP_BULLET_THRESHOLD;
    }

    private static boolean hasBoilerplatePrefix(final String cleaned) {
        final String lower = cleaned.toLowerCase(Locale.ROOT);
        return BOILERPLATE_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    private static int score(final int storyLength) {
        if (storyLength <= IDEAL_LENGTH) {
            return storyLength;
        }
        return IDEAL_LENGTH - OVERSHOOT_PENALTY * (storyLength - IDEAL_LENGTH);
    }

    /**
     * The outcome of assessing a description.
     *
     * @param cleaned the markup-stripped text, cut at a sentence end when over-long
     * @param usable true when the cleaned text is a usable blurb
     * @param score the quality score, higher is better, 0 when not usable
     */
    public record Assessment(String cleaned, boolean usable, int score) {
    }
}
