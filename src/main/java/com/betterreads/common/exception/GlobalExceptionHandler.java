package com.betterreads.common.exception;

import com.betterreads.common.dto.ApiErrorResponse;
import com.betterreads.common.dto.ApiErrorResponse.FieldError;
import com.betterreads.common.util.LogSanitizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps exceptions to {@link ApiErrorResponse}. 4xx outcomes log at WARN; the catch-all 500 logs
 * at ERROR with stack trace.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(final ResourceNotFoundException exception) {
        LOG.warn("Resource not found: {}", LogSanitizer.forLog(Objects.requireNonNullElse(exception.getMessage(), "")));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        Objects.requireNonNullElse(exception.getMessage(), "Resource not found"),
                        Instant.now(),
                        List.of()
                )
        );
    }

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

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(final BusinessRuleException exception) {
        LOG.warn("Business rule violation: {}",
                LogSanitizer.forLog(Objects.requireNonNullElse(exception.getMessage(), "")));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        Objects.requireNonNullElse(exception.getMessage(), "Business rule violation"),
                        Instant.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(final InvalidRequestException exception) {
        LOG.warn("Invalid request: {}",
                LogSanitizer.forLog(Objects.requireNonNullElse(exception.getMessage(), "")));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        Objects.requireNonNullElse(exception.getMessage(), "Invalid request"),
                        Instant.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(final HttpMessageNotReadableException exception) {
        final String message = "Malformed request body";
        LOG.warn(message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        message,
                        Instant.now(),
                        List.of()
                )
        );
    }

    /**
     * Maps an unsupported HTTP method to {@code 405 Method Not Allowed} with an {@code Allow}
     * header listing the supported methods. Without this handler, Spring's default handling
     * surfaces as a generic {@code 500} from the catch-all below, which masks operator signal
     * and pollutes 5xx alerting metrics.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            final HttpRequestMethodNotSupportedException exception) {
        LOG.warn("Method not allowed: method={}",
                LogSanitizer.forLog(Objects.requireNonNullElse(exception.getMethod(), "")));

        final Set<HttpMethod> supported = exception.getSupportedHttpMethods();
        final List<String> allowed = supported == null
                ? List.of()
                : supported.stream().map(HttpMethod::name).toList();

        final ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (!allowed.isEmpty()) {
            builder.header(HttpHeaders.ALLOW, allowed.toArray(String[]::new));
        }
        return builder.body(new ApiErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method not allowed",
                Instant.now(),
                List.of()
        ));
    }

    /**
     * Maps an unsupported request {@code Content-Type} to {@code 415 Unsupported Media Type}.
     * Without this handler, the default falls through to the catch-all {@code 500}, which is
     * wrong for a client error and disrupts metrics.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            final HttpMediaTypeNotSupportedException exception) {
        final MediaType received = exception.getContentType();
        LOG.warn("Unsupported content type: contentType={}",
                LogSanitizer.forLog(received == null ? "" : received.toString()));

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                new ApiErrorResponse(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        "Unsupported media type",
                        Instant.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(final BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Invalid credentials",
                        Instant.now(),
                        List.of()
                )
        );
    }

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

}
