package com.betterreads.collections.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/v1/me/books/{key}/favorite}.
 *
 * @param favorite true to mark the book a favorite, false to clear the flag
 */
public record SetFavoriteRequest(@NotNull Boolean favorite) {
}
