package com.betterreads.common.dto;

import org.jspecify.annotations.Nullable;

/**
 * Wraps every successful JSON response. {@code data} holds the resource or the list of resources;
 * {@code meta} carries pagination on a collection and is null on a single resource. Errors are
 * returned as {@code application/problem+json} (RFC 9457).
 */
public record ApiResponse<T>(
    T data,
    @Nullable ResponseMeta meta
) {

    /** Wraps a single resource with no pagination. */
    public static <T> ApiResponse<T> of(final T data) {
        return new ApiResponse<>(data, null);
    }

    /** Wraps a collection page with its pagination metadata. */
    public static <T> ApiResponse<T> of(final T data, final ResponseMeta meta) {
        return new ApiResponse<>(data, meta);
    }
}
