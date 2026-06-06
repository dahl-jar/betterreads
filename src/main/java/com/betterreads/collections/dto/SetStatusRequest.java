package com.betterreads.collections.dto;

import com.betterreads.collections.entity.ReadingStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/v1/me/books/{key}/status}.
 *
 * @param status the shelf to move the book to
 */
public record SetStatusRequest(@NotNull ReadingStatus status) {
}
