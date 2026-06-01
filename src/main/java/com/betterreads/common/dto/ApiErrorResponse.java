package com.betterreads.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Error response body returned for every 4xx and 5xx. {@code fieldErrors} is empty when the
 * failure is not a validation error.
 */
public record ApiErrorResponse(
    @Schema(example = "400") int status,
    @Schema(example = "Validation failed") String message,
    @Schema(example = "2026-05-13T08:30:00Z") Instant timestamp,
    List<FieldError> fieldErrors
) {

    public ApiErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    /** Per-field validation failure. {@code message} is null when the constraint defines none. */
    public record FieldError(
        @Schema(example = "email") String field,
        @Schema(example = "must be a well-formed email address") @Nullable String message
    ) { }
}
