package com.betterreads.collections.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PATCH /api/v1/me/books/{key}}. Each field is optional; a null leaves the stored
 * value unchanged.
 *
 * @param startedAt the date reading began
 * @param finishedAt the date reading finished
 * @param notes a private note on the book
 */
public record UpdateEntryRequest(
    @Nullable LocalDate startedAt,
    @Nullable LocalDate finishedAt,
    @Nullable @Size(max = 2000) String notes
) {
}
