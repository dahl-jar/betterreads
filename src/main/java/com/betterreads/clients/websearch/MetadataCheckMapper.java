package com.betterreads.clients.websearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.bookdescription.DescriptionQuality;
import com.betterreads.isbn.Isbn13;
import com.betterreads.isbn.IsbnLanguage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

final class MetadataCheckMapper {

    private static final int MAX_TITLE_LENGTH = 300;

    private static final int MAX_SERIES_LENGTH = 200;

    private static final int MAX_AUTHOR_LENGTH = 150;

    private static final int MAX_AUTHORS = 10;

    private static final int MAX_SERIES_POSITION = 999;

    private static final int EARLIEST_YEAR = 1450;

    private static final String VALUE = "value";

    private MetadataCheckMapper() {
    }

    static Map<Long, VerifiedMetadata> toMetadata(
        final JsonNode output, final Set<Long> askedIds, final List<String> allowedDomains) {
        final Map<Long, JsonNode> byId = output.path("books").valueStream()
            .filter(book -> book.path("id").canConvertToLong())
            .collect(Collectors.toMap(book -> book.path("id").asLong(), Function.identity(), (first, second) -> first));
        return askedIds.stream().collect(Collectors.toMap(Function.identity(),
            id -> byId.containsKey(id) ? toMetadata(byId.get(id), allowedDomains) : VerifiedMetadata.NONE));
    }

    private static VerifiedMetadata toMetadata(final JsonNode book, final List<String> allowedDomains) {
        final AllowedFields fields = new AllowedFields(book, allowedDomains);
        final JsonNode series = fields.field("series");
        final String seriesName = series.path("name").asString("").strip();
        final int seriesNumber = series.path("number").asInt(0);
        final boolean seriesValid =
            isText(seriesName, MAX_SERIES_LENGTH) && inRange(seriesNumber, 1, MAX_SERIES_POSITION);
        return new VerifiedMetadata(
            text(fields.field("title").path(VALUE), MAX_TITLE_LENGTH),
            authors(fields.field("authors").path(VALUE)),
            year(fields.field("year").path(VALUE)),
            seriesValid ? seriesName : null,
            seriesValid ? seriesNumber : null,
            description(fields.field("description").path(VALUE)),
            isbn(fields.field("isbn13").path(VALUE)));
    }

    private static @Nullable String text(final JsonNode value, final int maxLength) {
        final String text = value.asString("").strip();
        return isText(text, maxLength) ? text : null;
    }

    private static boolean isText(final String text, final int maxLength) {
        return !text.isEmpty() && text.length() <= maxLength && text.chars().noneMatch(Character::isISOControl);
    }

    private static @Nullable List<String> authors(final JsonNode value) {
        final List<String> names = value.valueStream().map(name -> name.asString("").strip()).toList();
        final boolean valid = !names.isEmpty() && names.size() <= MAX_AUTHORS
            && names.stream().allMatch(name -> isText(name, MAX_AUTHOR_LENGTH));
        return valid ? names : null;
    }

    private static @Nullable Integer year(final JsonNode value) {
        final int year = value.asInt(0);
        return inRange(year, EARLIEST_YEAR, Year.now(ZoneOffset.UTC).getValue() + 1) ? year : null;
    }

    private static @Nullable String description(final JsonNode value) {
        final DescriptionQuality.Assessment assessment = DescriptionQuality.assess(value.asString(""));
        return assessment.usable() ? assessment.cleaned() : null;
    }

    private static @Nullable String isbn(final JsonNode value) {
        final String isbn = value.asString("").replace("-", "").strip();
        return Isbn13.isValid(isbn) && IsbnLanguage.isEnglish(isbn) ? isbn : null;
    }

    private static boolean inRange(final int value, final int min, final int max) {
        return value >= min && value <= max;
    }

    private record AllowedFields(JsonNode book, List<String> allowedDomains) {

        JsonNode field(final String name) {
            final JsonNode field = book.path(name);
            return isAllowed(field.path("source").asString("")) ? field : MissingNode.getInstance();
        }

        private boolean isAllowed(final String source) {
            final @Nullable String host = hostOf(source);
            return host != null && allowedDomains.stream()
                .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        }

        private static @Nullable String hostOf(final String source) {
            try {
                final @Nullable String host = new URI(source).getHost();
                return host == null ? null : host.toLowerCase(Locale.ROOT);
            } catch (URISyntaxException ex) {
                return null;
            }
        }
    }
}
