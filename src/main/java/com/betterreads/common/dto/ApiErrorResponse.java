package com.betterreads.common.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    int status,
    String message,
    Instant timestamp,
    List<FieldError> fieldErrors
) {

    public ApiErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    public record FieldError(String field, String message) { }
}
