package com.betterreads.bookdescription;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.text.EnglishText;

/**
 * Decides whether a source description is a usable blurb and scores the usable ones.
 *
 * <p>Sources wrap the story in publication facts, so those sentences are stripped at the edges and
 * penalized inside.
 */
public final class DescriptionQuality {

    private static final int MIN_LENGTH = 40;

    private static final int IDEAL_LENGTH = 600;

    private static final int MAX_LENGTH = 2400;

    private static final int OVERSHOOT_DIVISOR = 4;

    private static final int FACT_SENTENCE_PENALTY = 60;

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");

    private static final List<Pattern> PUBLICATION_FACTS = List.of(
        Pattern.compile("\\bpubli(?:sh(?:ed|er|ers|ing)|cation)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwritten by\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bbestselling author\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:novel|book|memoir|anthology|collection) by\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bthe (?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth"
            + "|\\d{1,2}(?:st|nd|rd|th))(?:\\s+and\\s+\\w+)?\\s+(?:book|novel|volume|instal?lment)\\s+in\\b",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:followed|preceded) by\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bconsists of\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\beditions?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\baudiobook\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcritics\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:critical|commercial) (?:acclaim|success)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwon the\\b[^.!?]*\\baward\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:ebundle|boxed? set|bind-up)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:major motion picture|prime video|netflix|hbo|now streaming"
            + "|television series|tv series)\\b", Pattern.CASE_INSENSITIVE));

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

    public static Assessment assess(final String raw) {
        final boolean dump = isDump(raw);
        final String text = cutAtSentenceEnd(
            withoutTrailingFacts(withoutLeadingFacts(DescriptionCleaner.clean(raw))));
        final Sentences sentences = sentences(text);
        final boolean usable = !dump
            && sentences.storyLength() >= MIN_LENGTH
            && text.length() <= MAX_LENGTH
            && !hasBoilerplatePrefix(text)
            && EnglishText.isEnglish(text);
        return new Assessment(text, usable, usable ? score(sentences) : 0);
    }

    private static String withoutLeadingFacts(final String cleaned) {
        String text = cleaned;
        while (true) {
            final Matcher firstBreak = SENTENCE_BREAK.matcher(text);
            final boolean hasBreak = firstBreak.find();
            final String first = hasBreak ? text.substring(0, firstBreak.start()) : text;
            if (!isPublicationFact(first)) {
                return text;
            }
            text = hasBreak ? text.substring(firstBreak.end()) : "";
        }
    }

    private static String withoutTrailingFacts(final String cleaned) {
        String text = cleaned;
        while (!text.isEmpty()) {
            int lastBreakEnd = -1;
            final Matcher breaks = SENTENCE_BREAK.matcher(text);
            while (breaks.find()) {
                lastBreakEnd = breaks.end();
            }
            final String last = lastBreakEnd < 0 ? text : text.substring(lastBreakEnd);
            if (!isPublicationFact(last)) {
                return text;
            }
            text = lastBreakEnd < 0 ? "" : text.substring(0, lastBreakEnd).stripTrailing();
        }
        return text;
    }

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

    private static Sentences sentences(final String cleaned) {
        int storyLength = 0;
        int factCount = 0;
        for (final String sentence : SENTENCE_BREAK.splitAsStream(cleaned).toList()) {
            if (isPublicationFact(sentence)) {
                factCount++;
            } else {
                storyLength += sentence.length();
            }
        }
        return new Sentences(storyLength, factCount);
    }

    private static boolean isPublicationFact(final String sentence) {
        return PUBLICATION_FACTS.stream().anyMatch(fact -> fact.matcher(sentence).find());
    }

    /** Cleaning flattens the headings and bullets that mark a catalog-wiki dump, so this checks the raw text. */
    private static boolean isDump(final String raw) {
        return HEADING.matcher(raw).find()
            || BOLD_HEADING.matcher(raw).find()
            || LINK_DEFINITION.matcher(raw).find()
            || BULLET_LIST_LINE.matcher(raw).results().count() >= DUMP_BULLET_THRESHOLD;
    }

    private static boolean hasBoilerplatePrefix(final String cleaned) {
        final String lower = cleaned.toLowerCase(Locale.ROOT);
        return BOILERPLATE_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    private static int score(final Sentences sentences) {
        final int storyLength = sentences.storyLength();
        final int base = storyLength <= IDEAL_LENGTH
            ? storyLength
            : IDEAL_LENGTH - (storyLength - IDEAL_LENGTH) / OVERSHOOT_DIVISOR;
        return base - FACT_SENTENCE_PENALTY * sentences.factCount();
    }

    private record Sentences(int storyLength, int factCount) {
    }

    /**
     * The outcome of assessing a description.
     *
     * @param cleaned the markup-stripped text, cut at a sentence end when over-long
     * @param score higher is better, 0 when not usable
     */
    public record Assessment(String cleaned, boolean usable, int score) {
    }
}
