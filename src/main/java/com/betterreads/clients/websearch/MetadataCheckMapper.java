package com.betterreads.clients.websearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.bookdescription.DescriptionQuality;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.isbn.Isbn13;
import com.betterreads.isbn.IsbnLanguage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

final class MetadataCheckMapper {

    private static final int MAX_TITLE_LENGTH = 300;

    private static final int MAX_AUTHOR_LENGTH = 150;

    private static final int MAX_AUTHORS = 10;

    private static final int EARLIEST_YEAR = 1450;

    private static final String VALUE = "value";

    private static final String SOURCE = "source";

    private MetadataCheckMapper() {
    }

    static Map<Long, VerifiedMetadata> toMetadata(
        final JsonNode output, final List<MetadataCheckRequest> asked, final List<String> allowedDomains) {
        final Map<Long, JsonNode> byId = output.path("books").valueStream()
            .filter(book -> book.path("id").canConvertToLong())
            .collect(Collectors.toMap(book -> book.path("id").asLong(), Function.identity(), (first, second) -> first));
        return asked.stream().collect(Collectors.toMap(MetadataCheckRequest::bookId,
            request -> byId.containsKey(request.bookId())
                ? toMetadata(byId.get(request.bookId()), request.isbn13(), allowedDomains)
                : VerifiedMetadata.NONE));
    }

    private static VerifiedMetadata toMetadata(
        final JsonNode book, final @Nullable String storedIsbn, final List<String> allowedDomains) {
        final AllowedFields fields = new AllowedFields(book, allowedDomains);
        final SeriesEntry series = NumberedSeries.from(fields.field("series"));
        final String englishIsbn = IsbnLanguage.isEnglish(storedIsbn) ? storedIsbn : null;
        return new VerifiedMetadata(
            title(fields.field("title"), englishIsbn),
            authors(fields.field("authors").path(VALUE)),
            year(fields.field("year").path(VALUE)),
            series == null ? null : series.name(),
            series == null ? null : series.position(),
            description(fields.field("description").path(VALUE)),
            englishIsbn == null ? isbn(fields.field("isbn13").path(VALUE)) : null,
            series == null ? null : NumberedSeries.from(fields.field("universe")));
    }

    private static @Nullable String title(final JsonNode field, final @Nullable String englishIsbn) {
        final String title = text(field.path(VALUE), MAX_TITLE_LENGTH);
        if (title == null || englishIsbn == null) {
            return title;
        }
        final String path = pathOf(field.path(SOURCE).asString("")).replace("-", "").toUpperCase(Locale.ROOT);
        final String isbn10 = Isbn13.toIsbn10(englishIsbn);
        return holdsIsbn(path, englishIsbn) || isbn10 != null && holdsIsbn(path, isbn10) ? title : null;
    }

    private static boolean holdsIsbn(final String path, final String isbn) {
        return Pattern.compile("(?<![0-9X])" + Pattern.quote(isbn) + "(?![0-9X])").matcher(path).find();
    }

    private static String pathOf(final String source) {
        try {
            final String path = new URI(source).getRawPath();
            return path == null ? "" : path;
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    private static @Nullable String text(final JsonNode value, final int maxLength) {
        final String text = value.asString("").strip();
        return isText(text, maxLength) ? text : null;
    }

    static boolean isText(final String text, final int maxLength) {
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

    static boolean inRange(final int value, final int min, final int max) {
        return value >= min && value <= max;
    }

    private record AllowedFields(JsonNode book, List<String> allowedDomains) {

        JsonNode field(final String name) {
            final JsonNode field = book.path(name);
            return isAllowed(field.path(SOURCE).asString("")) ? field : MissingNode.getInstance();
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
