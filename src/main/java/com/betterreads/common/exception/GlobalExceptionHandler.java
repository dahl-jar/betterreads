package com.betterreads.common.exception;

import com.betterreads.common.dto.ApiErrorResponse;
import com.betterreads.common.dto.ApiErrorResponse.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle Resource Not Found Exception
     * Response Status: 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(final ResourceNotFoundException exception) {
        LOG.warn("Resource not found: {}", sanitize(Objects.requireNonNullElse(exception.getMessage(), "")));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        Objects.requireNonNullElse(exception.getMessage(), "Resource not found"),
                        Instant.now(),
                        List.of()
                )
        );
    }

    /**
     * Handle Validation Exception
     * Response Status: 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(final MethodArgumentNotValidException exception) {
        final List<FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        LOG.warn("Validation failed: {} field errors", fieldErrors.size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        Instant.now(),
                        fieldErrors
                )
        );
    }

    /**
     * Handle Business Rule Exception
     * Response Status: 409
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(final BusinessRuleException exception) {
        LOG.warn("Business rule violation: {}", sanitize(Objects.requireNonNullElse(exception.getMessage(), "")));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        Objects.requireNonNullElse(exception.getMessage(), "Business rule violation"),
                        Instant.now(),
                        List.of()
                )
        );
    }

    /**
     * Handle unexpected exceptions
     * Response Status: 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(final Exception exception) {
        LOG.error("Unexpected error", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred",
                        Instant.now(),
                        List.of()
                )
        );
    }

    private static String sanitize(final String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\n", "").replace("\r", "");
    }
}
