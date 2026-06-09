package com.betterreads.catalog.service.source;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips HTML and Markdown markup from a source description so the catalog stores plain prose.
 *
 * <p>Google returns HTML; OpenLibrary returns Markdown with emphasis markers, footnote references,
 * and reference-style links. Markup is removed and whitespace normalized.
 */
public final class DescriptionCleaner {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]++>");

    private static final Pattern INLINE_LINK = Pattern.compile("\\[([^\\]]++)]\\([^)]*+\\)");

    private static final Pattern REFERENCE_LINK = Pattern.compile("\\[([^\\]]++)]\\[[^\\]]*+]");

    private static final Pattern LINK_DEFINITION =
        Pattern.compile("(?m)^\\s*+\\[[^\\]]++]:\\s*+\\S++\\s*+$");

    private static final Pattern FOOTNOTE_REFERENCE = Pattern.compile("\\[\\d++]");

    private static final Pattern EMPHASIS = Pattern.compile("\\*{1,3}+([^*]++)\\*{1,3}+");

    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s*");

    private static final Pattern LIST_BULLET = Pattern.compile("(?m)^\\s*[-*]\\s+");

    private static final Pattern THEMATIC_BREAK = Pattern.compile("(?m)^\\s*-{3,}\\s*$");

    private static final Pattern BLANK_RUN = Pattern.compile("\\n{3,}");

    private static final Pattern TRAILING_SPACE = Pattern.compile("(?m)[ \\t]+$");

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]++);");

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t\\u00a0]{2,}");

    private static final int HEX_RADIX = 16;

    private static final int DECIMAL_RADIX = 10;

    private static final int NON_BREAKING_SPACE = 0x00a0;

    private DescriptionCleaner() {
    }

    /** Returns the description with HTML and Markdown markup removed and whitespace normalized. */
    public static String clean(final String description) {
        if (description.isBlank()) {
            return "";
        }
        String text = description.replace("\r\n", "\n").replace('\r', '\n');
        text = HTML_TAG.matcher(text).replaceAll("");
        text = decodeEntities(text);
        text = decodeNumericEntities(text);
        text = INLINE_LINK.matcher(text).replaceAll("$1");
        text = REFERENCE_LINK.matcher(text).replaceAll("$1");
        text = LINK_DEFINITION.matcher(text).replaceAll("");
        text = FOOTNOTE_REFERENCE.matcher(text).replaceAll("");
        text = EMPHASIS.matcher(text).replaceAll("$1");
        text = HEADING.matcher(text).replaceAll("");
        text = LIST_BULLET.matcher(text).replaceAll("");
        text = THEMATIC_BREAK.matcher(text).replaceAll("");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = TRAILING_SPACE.matcher(text).replaceAll("");
        text = BLANK_RUN.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    private static String decodeNumericEntities(final String text) {
        return NUMERIC_ENTITY.matcher(text).replaceAll(match -> {
            final int radix = match.group(1).isEmpty() ? DECIMAL_RADIX : HEX_RADIX;
            final int codePoint = Integer.parseInt(match.group(2), radix);
            final String decoded = codePoint == NON_BREAKING_SPACE ? " " : Character.toString(codePoint);
            return Matcher.quoteReplacement(decoded);
        });
    }

    private static String decodeEntities(final String text) {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&nbsp;", " ");
    }
}
