package com.betterreads.book;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.betterreads.booksource.NullableLists;
import com.betterreads.booksource.SeriesEntry;
import org.jspecify.annotations.Nullable;

public record VerifiedMetadata(
    @Nullable String title,
    @Nullable List<String> authors,
    @Nullable Integer year,
    @Nullable String seriesName,
    @Nullable Double seriesPosition,
    @Nullable String description,
    @Nullable String isbn13,
    @Nullable SeriesEntry universe,
    boolean seriesCleared,
    boolean descriptionCleared,
    Map<VerifiedField, FieldEvidence> evidence
) {

    public static final VerifiedMetadata NONE =
        new VerifiedMetadata(null, null, null, null, null, null, null, null);

    public VerifiedMetadata {
        authors = NullableLists.copyOf(authors);
        final Map<VerifiedField, FieldEvidence> copy = new EnumMap<>(VerifiedField.class);
        copy.putAll(evidence);
        evidence = Collections.unmodifiableMap(copy);
    }

    // checkstyle:ParameterNumber, PMD.ExcessiveParameterList: the field values alone, with no clears or evidence.
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    public VerifiedMetadata(
        final @Nullable String title,
        final @Nullable List<String> authors,
        final @Nullable Integer year,
        final @Nullable String seriesName,
        final @Nullable Double seriesPosition,
        final @Nullable String description,
        final @Nullable String isbn13,
        final @Nullable SeriesEntry universe
    ) {
        this(title, authors, year, seriesName, seriesPosition, description, isbn13, universe, false, false, Map.of());
    }

    @Override
    @Nullable
    public List<String> authors() {
        return NullableLists.copyOf(authors);
    }

    public boolean isEmpty() {
        return title == null && authors == null && year == null && seriesName == null && description == null
            && isbn13 == null && !seriesCleared && !descriptionCleared;
    }
}
