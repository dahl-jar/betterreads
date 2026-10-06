package com.betterreads.clients.websearch;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.betterreads.booksource.SeriesEntry;
import com.betterreads.isbn.Isbn13;
import com.betterreads.text.TextMatch;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class QuoteMatch {

    private static final Pattern NUMBER =
        Pattern.compile("(?<!\\p{N})(?<!\\p{N}\\.)(-?)(\\p{N}+(?:\\.\\p{N}+)?)(?!\\p{N})(?!\\.\\p{N})");

    private static final Pattern JOINED_AFTER = Pattern.compile("^\\s*[-/,]\\s*\\p{N}");

    private static final Pattern JOINED_BEFORE = Pattern.compile("\\p{N}\\s*[-/,]?\\s*$");

    private static final List<String> SEARCH_PATHS = List.of("search", "special:");

    private QuoteMatch() {
    }

    static boolean echoesUrl(final String url, final List<String> texts) {
        final String echo = echo(url);
        return !echo.isBlank() && texts.stream().anyMatch(text -> PageText.hasWords(echo, text));
    }

    private static String echo(final String url) {
        try {
            final URI uri = URI.create(url);
            final String query = Optional.ofNullable(uri.getRawQuery()).orElse("");
            final String path = Optional.ofNullable(uri.getRawPath()).orElse("");
            final String lowerPath = path.toLowerCase(Locale.ROOT);
            final boolean searchPath = SEARCH_PATHS.stream().anyMatch(lowerPath::contains);
            return URLDecoder.decode(query + " " + (searchPath ? path : ""), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return url;
        }
    }

    static boolean namesTitle(final String text, final MetadataCheckRequest asked) {
        final String title = TextMatch.coreTitle(asked.title());
        final int titleWords = PageText.wordCount(title);
        final String universe = Optional.ofNullable(asked.universe()).map(SeriesEntry::name).orElse(null);
        final List<String> others = Stream.concat(
                Stream.of(asked.seriesName(), universe),
                asked.seriesBooks().stream().map(SeriesBook::title))
            .filter(Objects::nonNull)
            .filter(other -> PageText.wordCount(other) > titleWords || !PageText.hasWords(other, title))
            .toList();
        return PageText.hasWords(PageText.without(text, others), title);
    }

    static List<String> valueTexts(final FieldVerdict verdict) {
        final JsonNode value = verdict.value();
        return switch (verdict.field()) {
            case AUTHORS -> value.valueStream().map(name -> name.asString("")).toList();
            case SERIES, UNIVERSE -> List.of(value.path("name").asString(""));
            case TITLE, YEAR, ISBN -> List.of(value.asString(""));
        };
    }

    static boolean showsValue(final FieldVerdict verdict, final MetadataCheckRequest asked) {
        final String quote = verdict.quote();
        final JsonNode value = verdict.value();
        return switch (verdict.field()) {
            case TITLE -> PageText.hasWords(quote, value.asString(""));
            case AUTHORS -> value.valueStream().allMatch(name -> PageText.hasWords(quote, name.asString("")));
            case YEAR -> numbers(quote).contains(BigDecimal.valueOf(value.asInt()).stripTrailingZeros());
            case ISBN -> holdsIsbn(quote, value.asString(""));
            case SERIES -> seriesInQuote(value, quote, asked.seriesPosition());
            case UNIVERSE -> seriesInQuote(value, quote, Optional.ofNullable(asked.universe())
                .map(SeriesEntry::position).orElse(null));
        };
    }

    static boolean holdsIsbn(final String text, final String isbn) {
        final String joined = PageText.normalize(text).replace("-", "");
        final String isbn13 = isbn.replace("-", "").strip();
        final String isbn10 = Isbn13.toIsbn10(isbn13);
        return PageText.hasWords(joined, isbn13) || isbn10 != null && PageText.hasWords(joined, isbn10);
    }

    private static boolean seriesInQuote(final JsonNode value, final String quote, final @Nullable Double stored) {
        final SeriesEntry series = Objects.requireNonNull(NumberedSeries.from(value));
        final boolean numberKept = stored != null && Double.compare(stored, series.position()) == 0;
        final BigDecimal number = BigDecimal.valueOf(value.path("number").asDouble()).stripTrailingZeros();
        return PageText.hasWords(quote, series.name()) && (numberKept || numbers(quote).contains(number));
    }

    private static Set<BigDecimal> numbers(final String quote) {
        final String text = PageText.normalize(quote);
        final Matcher matcher = NUMBER.matcher(text);
        return matcher.results()
            .filter(found -> !JOINED_AFTER.matcher(text.substring(found.end())).find())
            .filter(found -> !JOINED_BEFORE.matcher(text.substring(0, found.start())).find())
            .map(found -> new BigDecimal(found.group(1) + found.group(2)).stripTrailingZeros())
            .collect(Collectors.toSet());
    }
}
