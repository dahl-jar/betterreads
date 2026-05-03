package com.betterreads.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error envelope returned for every 4xx and 5xx response. {@code fieldErrors} is empty
 * for non-validation errors.
 */
public record ApiErrorResponse(
    int status,
    String message,
    Instant timestamp,
    List<FieldError> fieldErrors
) {

    public ApiErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    /**
     * Per-field validation failure surfaced inside {@link ApiErrorResponse#fieldErrors}.
     */
    public record FieldError(String field, String message) { }
}
