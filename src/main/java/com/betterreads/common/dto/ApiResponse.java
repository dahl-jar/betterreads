package com.betterreads.common.dto;

import org.jspecify.annotations.Nullable;

/**
 * The success envelope every JSON response is wrapped in. {@code data} holds the resource or the
 * list of resources; {@code meta} carries pagination on a collection and is absent on a single
 * resource. Error responses use {@code application/problem+json} (RFC 9457) instead of this shape.
 *
 * @param data the resource, or the list of resources for a collection
 * @param meta pagination for a collection, null for a single resource
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
