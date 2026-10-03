package com.betterreads.features.metadatacheck;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.clients.websearch.CheckRun;
import com.betterreads.clients.websearch.CheckedBook;
import com.betterreads.clients.websearch.FieldOutcome;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.clients.websearch.SearchUsage;

final class MetadataCheckSamples {

    static final int BATCH_SIZE = 10;

    static final int MAX_BOOKS = 100;

    private static final SearchUsage USAGE = new SearchUsage(3, 0.25, 35_210, 0);

    private static final List<String> FIELDS = List.of(MetadataJson.TITLE_FIELD, MetadataJson.AUTHORS_FIELD,
        MetadataJson.YEAR_FIELD, MetadataJson.SERIES_FIELD, MetadataJson.UNIVERSE_FIELD,
        MetadataJson.DESCRIPTION_FIELD, MetadataJson.ISBN_FIELD);

    private MetadataCheckSamples() {
    }

    static MetadataCheckProperties properties(final boolean enabled) {
        return new MetadataCheckProperties(enabled, BATCH_SIZE, MAX_BOOKS);
    }

    static CheckRun run(final Map<Long, CheckedBook> books) {
        return new CheckRun(books, USAGE);
    }

    static CheckedBook confirmed(final VerifiedMetadata metadata) {
        return checked(metadata, FieldOutcome.CONFIRMED);
    }

    static CheckedBook unconfirmed() {
        return checked(VerifiedMetadata.NONE, FieldOutcome.NOT_ANSWERED);
    }

    private static CheckedBook checked(final VerifiedMetadata metadata, final FieldOutcome outcome) {
        final Map<String, FieldOutcome> outcomes = FIELDS.stream()
            .collect(Collectors.toMap(Function.identity(), field -> outcome, (first, second) -> first,
                LinkedHashMap::new));
        return new CheckedBook(metadata, outcomes, MetadataJson.answer());
    }
}
