package com.betterreads.features.metadatacheck;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.book.Book;
import com.betterreads.book.FieldEvidence;
import com.betterreads.book.VerifiedField;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.websearch.CheckOutcome;
import com.betterreads.clients.websearch.CheckRun;
import com.betterreads.clients.websearch.CheckedBook;
import com.betterreads.clients.websearch.FieldOutcome;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.clients.websearch.SearchUsage;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.TemporalUnitOffset;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class MetadataCheckSamples {

    static final int BATCH_SIZE = 10;

    static final int MAX_BOOKS = 100;

    static final int MAX_ATTEMPTS = 3;

    static final int CHECK_VERSION = 2;

    static final TemporalUnitOffset A_MINUTE = Assertions.within(1, ChronoUnit.MINUTES);

    static final Duration RETRY_AFTER_FAILURE = Duration.ofDays(1);

    static final Duration RETRY_AFTER_UNCONFIRMED = Duration.ofDays(7);

    private static final SearchUsage USAGE = new SearchUsage(3, 0.25, 35_210, 0);

    private static final FieldEvidence EVIDENCE = new FieldEvidence(MetadataJson.SOURCE, "first published in 2014");

    private static final List<String> FIELDS = List.of(MetadataJson.TITLE_FIELD, MetadataJson.AUTHORS_FIELD,
        MetadataJson.YEAR_FIELD, MetadataJson.SERIES_FIELD, MetadataJson.UNIVERSE_FIELD,
        MetadataJson.DESCRIPTION_FIELD, MetadataJson.ISBN_FIELD);

    private MetadataCheckSamples() {
    }

    static MetadataCheckProperties properties(final boolean enabled) {
        return new MetadataCheckProperties(enabled, BATCH_SIZE, MAX_BOOKS, RETRY_AFTER_FAILURE, RETRY_AFTER_UNCONFIRMED,
            MAX_ATTEMPTS, CHECK_VERSION);
    }

    static CheckOutcome outcome(final Map<Long, CheckedBook> books) {
        return new CheckOutcome.Checked(new CheckRun(books, USAGE));
    }

    static CheckedBook confirmed(final VerifiedMetadata metadata) {
        return checked(metadata, FieldOutcome.ACCEPTED);
    }

    static CheckedBook unconfirmed() {
        return checked(VerifiedMetadata.NONE, FieldOutcome.NOT_ANSWERED);
    }

    static VerifiedMetadata yearWithEvidence() {
        return new VerifiedMetadata(null, null, MetadataJson.YEAR, null, null, null, null, null, false, false,
            Map.of(VerifiedField.YEAR, EVIDENCE));
    }

    static VerifiedMetadata clearsOnly() {
        return new VerifiedMetadata(null, null, null, null, null, null, null, null, true, true,
            Map.of(VerifiedField.SERIES, EVIDENCE));
    }

    static CheckedBook unreachable(final String field, final String source) {
        final ObjectNode answer = new JsonMapper().createObjectNode();
        answer.putObject(field).put("source", source);
        return new CheckedBook(VerifiedMetadata.NONE, Map.of(field, FieldOutcome.PAGE_UNREACHABLE), answer);
    }

    private static CheckedBook checked(final VerifiedMetadata metadata, final FieldOutcome outcome) {
        final Map<String, FieldOutcome> outcomes = FIELDS.stream()
            .collect(Collectors.toMap(Function.identity(), field -> outcome, (first, second) -> first,
                LinkedHashMap::new));
        return new CheckedBook(metadata, outcomes, MetadataJson.answer());
    }

    static Book withGenre(final Book book, final String genre) {
        book.applyFrom(SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL" + book.getBookId() + "W")
            .title(book.getTitle())
            .rawSubjects(List.of(genre))
            .build());
        return book;
    }

    static OffsetDateTime after(final Duration wait) {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(wait);
    }
}
