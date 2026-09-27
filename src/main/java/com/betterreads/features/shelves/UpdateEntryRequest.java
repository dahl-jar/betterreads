package com.betterreads.features.shelves;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/** A null field leaves the stored value unchanged. */
record UpdateEntryRequest(
    @Nullable LocalDate startedAt,
    @Nullable LocalDate finishedAt,
    @Nullable @Size(max = 2000) String notes
) {
}
