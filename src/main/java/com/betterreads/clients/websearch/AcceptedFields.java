package com.betterreads.clients.websearch;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.betterreads.book.FieldEvidence;
import com.betterreads.book.VerifiedField;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

final class AcceptedFields {

    private final Map<VerifiedField, FieldEvidence> evidence = new EnumMap<>(VerifiedField.class);

    private @Nullable String title;

    private @Nullable List<String> authors;

    private @Nullable Integer year;

    private @Nullable SeriesEntry series;

    private @Nullable SeriesEntry universe;

    private @Nullable String description;

    private @Nullable String isbn;

    private boolean seriesCleared;

    private boolean descriptionCleared;

    void add(final FieldVerdict verdict) {
        final JsonNode value = verdict.value();
        final Runnable store = switch (verdict.field()) {
            case TITLE -> () -> title = FieldValues.title(value);
            case AUTHORS -> () -> authors = FieldValues.authors(value);
            case YEAR -> () -> year = FieldValues.year(value);
            case ISBN -> () -> isbn = FieldValues.isbn(value);
            case SERIES -> () -> addSeries(verdict);
            case UNIVERSE -> () -> universe = NumberedSeries.from(value);
        };
        store.run();
        verdict.field().verified()
            .ifPresent(field -> evidence.put(field, new FieldEvidence(verdict.source(), verdict.quote())));
    }

    private void addSeries(final FieldVerdict verdict) {
        if (verdict.status() == VerdictStatus.CLEAR) {
            seriesCleared = true;
        } else {
            series = NumberedSeries.from(verdict.value());
        }
    }

    boolean universeWithoutSeries() {
        return universe != null && series == null;
    }

    void storeDescription(final String text, final FieldEvidence quoted) {
        description = text;
        evidence.put(VerifiedField.DESCRIPTION, quoted);
    }

    void clearDescription(final FieldEvidence quoted) {
        descriptionCleared = true;
        evidence.put(VerifiedField.DESCRIPTION, quoted);
    }

    VerifiedMetadata metadata() {
        final SeriesEntry primary = series;
        return new VerifiedMetadata(
            title,
            authors,
            year,
            primary == null ? null : primary.name(),
            primary == null ? null : primary.position(),
            description,
            isbn,
            primary == null ? null : universe,
            seriesCleared,
            descriptionCleared,
            evidence);
    }
}
