package com.betterreads.clients.websearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.betterreads.isbn.Isbn13;
import com.betterreads.isbn.IsbnLanguage;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class MetadataCheckMapper {

    static final String DESCRIPTION_FIELD = "description";

    private static final String VALUE = "value";

    private static final String SOURCE = "source";

    private static final String QUOTE = "quote";

    private static final String STATUS = "status";

    private MetadataCheckMapper() {
    }

    static Map<Long, FieldVerdicts> toVerdicts(
        final JsonNode output, final List<MetadataCheckRequest> asked, final List<String> allowedDomains) {
        final Map<Long, JsonNode> byId = output.path("books").valueStream()
            .filter(book -> book.path("id").canConvertToLong())
            .collect(Collectors.toMap(book -> book.path("id").asLong(), Function.identity(), (first, second) -> first));
        return asked.stream()
            .flatMap(request -> Optional.ofNullable(byId.get(request.bookId()))
                .map(book -> Map.entry(request.bookId(), toVerdicts(book, request, allowedDomains)))
                .stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static FieldVerdicts toVerdicts(
        final JsonNode book, final MetadataCheckRequest asked, final List<String> allowedDomains) {
        final String englishIsbn = IsbnLanguage.isEnglish(asked.isbn13()) ? asked.isbn13() : null;
        final Context context = new Context(allowedDomains, englishIsbn);
        final Map<CheckedField, Optional<FieldOutcome>> rejections = new EnumMap<>(CheckedField.class);
        Arrays.stream(CheckedField.values())
            .forEach(field -> rejections.put(field, rejection(field, book.path(field.key()), context)));
        final Map<String, FieldOutcome> outcomes = new LinkedHashMap<>();
        rejections.forEach((field, rejected) -> outcomes.put(field.key(), rejected.orElse(FieldOutcome.NOT_ANSWERED)));
        final List<FieldVerdict> fields = rejections.entrySet().stream()
            .filter(rejected -> rejected.getValue().isEmpty())
            .map(rejected -> verdict(rejected.getKey(), book.path(rejected.getKey().key())))
            .toList();
        final DescriptionVerdict description =
            listedDescription(book.path(DESCRIPTION_FIELD), allowedDomains, outcomes);
        return new FieldVerdicts(fields, description, outcomes, book);
    }

    private static DescriptionVerdict listedDescription(
        final JsonNode node, final List<String> allowedDomains, final Map<String, FieldOutcome> outcomes) {
        final Optional<DescriptionVerdict> verdict = description(node);
        final boolean listed = SourceHosts.isListed(node.path(SOURCE).asString(""), allowedDomains);
        outcomes.put(DESCRIPTION_FIELD, verdict.isPresent() && !listed
            ? FieldOutcome.SOURCE_NOT_ALLOWED : FieldOutcome.NOT_ANSWERED);
        return verdict.filter(found -> listed).orElse(DescriptionVerdict.NONE);
    }

    private static FieldVerdict verdict(final CheckedField field, final JsonNode node) {
        return new FieldVerdict(field, statusOf(node, VerdictStatus.class).orElse(VerdictStatus.NOT_FOUND),
            node.path(VALUE), node.path(QUOTE).asString(""), node.path(SOURCE).asString(""));
    }

    private static Optional<DescriptionVerdict> description(final JsonNode node) {
        return statusOf(node, DescriptionStatus.class)
            .filter(status -> status != DescriptionStatus.NOT_FOUND)
            .map(status -> new DescriptionVerdict(status, node.path(SOURCE).asString(""),
                node.path("start").asString(""), node.path("end").asString(""), node.path(QUOTE).asString("")));
    }

    private static Optional<FieldOutcome> rejection(
        final CheckedField field, final JsonNode node, final Context context) {
        final VerdictStatus status = statusOf(node, VerdictStatus.class).orElse(VerdictStatus.NOT_FOUND);
        final boolean clear = status == VerdictStatus.CLEAR;
        if (isUnanswered(status, node.path(VALUE))) {
            return Optional.of(FieldOutcome.NOT_ANSWERED);
        }
        if (clear && field != CheckedField.SERIES) {
            return Optional.of(FieldOutcome.VALUE_REJECTED);
        }
        final String source = node.path(SOURCE).asString("");
        if (!SourceHosts.isListed(source, context.allowedDomains())) {
            return Optional.of(FieldOutcome.SOURCE_NOT_ALLOWED);
        }
        return clear ? Optional.empty() : valueRejection(field, node.path(VALUE), source, context);
    }

    private static boolean isUnanswered(final VerdictStatus status, final JsonNode value) {
        return status == VerdictStatus.NOT_FOUND || status != VerdictStatus.CLEAR && FieldValues.isEmpty(value);
    }

    private static Optional<FieldOutcome> valueRejection(
        final CheckedField field, final JsonNode value, final String source, final Context context) {
        if (!FieldValues.parses(field, value) || !context.fits(field)) {
            return Optional.of(FieldOutcome.VALUE_REJECTED);
        }
        final String englishIsbn = context.englishIsbn();
        final boolean titleOffIsbnPage =
            field == CheckedField.TITLE && englishIsbn != null && !isbnPage(source, englishIsbn);
        return titleOffIsbnPage ? Optional.of(FieldOutcome.TITLE_SOURCE_WITHOUT_ISBN) : Optional.empty();
    }

    private static <E extends Enum<E>> Optional<E> statusOf(final JsonNode field, final Class<E> type) {
        final String text = field.path(STATUS).asString("");
        return EnumSet.allOf(type).stream().filter(value -> value.name().equalsIgnoreCase(text)).findFirst();
    }

    private static boolean isbnPage(final String source, final String englishIsbn) {
        final String path = pathOf(source).replace("-", "").toUpperCase(Locale.ROOT);
        final String isbn10 = Isbn13.toIsbn10(englishIsbn);
        final boolean holdsIsbn10 = isbn10 != null && holdsIsbn(path, isbn10);
        return holdsIsbn(path, englishIsbn) || holdsIsbn10;
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

    private record Context(List<String> allowedDomains, @Nullable String englishIsbn) {

        boolean fits(final CheckedField field) {
            return field != CheckedField.ISBN || englishIsbn == null;
        }
    }
}
