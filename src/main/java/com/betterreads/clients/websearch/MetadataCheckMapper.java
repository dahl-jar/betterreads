package com.betterreads.clients.websearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import tools.jackson.databind.node.ObjectNode;

final class MetadataCheckMapper {

    private static final int MAX_TITLE_LENGTH = 300;

    private static final int MAX_AUTHOR_LENGTH = 150;

    private static final int MAX_AUTHORS = 10;

    private static final int MAX_LOGGED_DESCRIPTION = 200;

    private static final int EARLIEST_YEAR = -3000;

    private static final String VALUE = "value";

    private static final String SOURCE = "source";

    private static final String TITLE_FIELD = "title";

    private static final String AUTHORS_FIELD = "authors";

    private static final String YEAR_FIELD = "year";

    private static final String SERIES_FIELD = "series";

    private static final String UNIVERSE_FIELD = "universe";

    private static final String DESCRIPTION_FIELD = "description";

    private static final String ISBN_FIELD = "isbn13";

    private static final List<String> FIELDS = List.of(TITLE_FIELD, AUTHORS_FIELD, YEAR_FIELD, SERIES_FIELD,
        UNIVERSE_FIELD, DESCRIPTION_FIELD, ISBN_FIELD);

    private MetadataCheckMapper() {
    }

    static Map<Long, CheckedBook> toCheckedBooks(
        final JsonNode output, final List<MetadataCheckRequest> asked, final List<String> allowedDomains) {
        final Map<Long, JsonNode> byId = output.path("books").valueStream()
            .filter(book -> book.path("id").canConvertToLong())
            .collect(Collectors.toMap(book -> book.path("id").asLong(), Function.identity(), (first, second) -> first));
        return asked.stream()
            .flatMap(request -> Optional.ofNullable(byId.get(request.bookId()))
                .map(book -> Map.entry(request.bookId(), toCheckedBook(book, request.isbn13(), allowedDomains)))
                .stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static CheckedBook toCheckedBook(
        final JsonNode book, final @Nullable String storedIsbn, final List<String> allowedDomains) {
        final CheckedFields fields = new CheckedFields(book, allowedDomains);
        final String englishIsbn = IsbnLanguage.isEnglish(storedIsbn) ? storedIsbn : null;
        final SeriesEntry series = fields.value(SERIES_FIELD, NumberedSeries::from);
        final VerifiedMetadata metadata = new VerifiedMetadata(
            title(fields, englishIsbn),
            fields.value(AUTHORS_FIELD, field -> authors(field.path(VALUE))),
            fields.value(YEAR_FIELD, field -> year(field.path(VALUE))),
            series == null ? null : series.name(),
            series == null ? null : series.position(),
            fields.value(DESCRIPTION_FIELD, field -> description(field.path(VALUE))),
            fields.valueWhen(englishIsbn == null, ISBN_FIELD, field -> isbn(field.path(VALUE))),
            fields.valueWhen(series != null, UNIVERSE_FIELD, NumberedSeries::from));
        return new CheckedBook(metadata, fields.outcomes(), withShortDescription(book));
    }

    private static JsonNode withShortDescription(final JsonNode book) {
        final JsonNode copy = book.deepCopy();
        if (copy.path(DESCRIPTION_FIELD) instanceof ObjectNode description && description.path(VALUE).isString()) {
            final String text = description.path(VALUE).asString();
            description.put(VALUE, text.substring(0, Math.min(text.length(), MAX_LOGGED_DESCRIPTION)));
        }
        return copy;
    }

    private static @Nullable String title(final CheckedFields fields, final @Nullable String englishIsbn) {
        final String title = fields.value(TITLE_FIELD, field -> text(field.path(VALUE), MAX_TITLE_LENGTH));
        if (title == null || englishIsbn == null) {
            return title;
        }
        final String path = pathOf(fields.source(TITLE_FIELD)).replace("-", "").toUpperCase(Locale.ROOT);
        final String isbn10 = Isbn13.toIsbn10(englishIsbn);
        final boolean holdsIsbn10 = isbn10 != null && holdsIsbn(path, isbn10);
        if (holdsIsbn(path, englishIsbn) || holdsIsbn10) {
            return title;
        }
        fields.drop(TITLE_FIELD, FieldOutcome.TITLE_SOURCE_WITHOUT_ISBN);
        return null;
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
        return value.isIntegralNumber() && year != 0
            && inRange(year, EARLIEST_YEAR, Year.now(ZoneOffset.UTC).getValue() + 1) ? year : null;
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

    @FunctionalInterface
    private interface FieldReader<T> {

        @Nullable T read(JsonNode field);
    }

    private static final class CheckedFields {

        private final JsonNode book;

        private final List<String> allowedDomains;

        private final Map<String, FieldOutcome> recorded = new LinkedHashMap<>();

        CheckedFields(final JsonNode book, final List<String> allowedDomains) {
            this.book = book;
            this.allowedDomains = allowedDomains;
            FIELDS.forEach(name -> recorded.put(name, FieldOutcome.NOT_ANSWERED));
        }

        <T> @Nullable T value(final String name, final FieldReader<T> reader) {
            final JsonNode field = book.path(name);
            final boolean allowed = isAllowed(source(name));
            final T value = allowed ? reader.read(field) : null;
            recorded.put(name, outcome(field, allowed, value != null));
            return value;
        }

        <T> @Nullable T valueWhen(final boolean usable, final String name, final FieldReader<T> reader) {
            final T value = value(name, reader);
            if (usable) {
                return value;
            }
            drop(name, FieldOutcome.VALUE_REJECTED);
            return null;
        }

        void drop(final String name, final FieldOutcome outcome) {
            recorded.replace(name, FieldOutcome.CONFIRMED, outcome);
        }

        String source(final String name) {
            return book.path(name).path(SOURCE).asString("");
        }

        Map<String, FieldOutcome> outcomes() {
            return recorded;
        }

        private static FieldOutcome outcome(final JsonNode field, final boolean allowed, final boolean read) {
            if (read) {
                return FieldOutcome.CONFIRMED;
            }
            final boolean answered = field.propertyStream()
                .anyMatch(property -> !SOURCE.equals(property.getKey()) && !property.getValue().isNull());
            if (!answered) {
                return FieldOutcome.NOT_ANSWERED;
            }
            return allowed ? FieldOutcome.VALUE_REJECTED : FieldOutcome.SOURCE_NOT_ALLOWED;
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
