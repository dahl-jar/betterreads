package com.betterreads.common.exception;

import com.betterreads.common.util.LogSanitizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps exceptions to RFC 9457 {@link ProblemDetail} responses, served as
 * {@code application/problem+json}. A validation failure carries an {@code errors} extension member
 * listing the offending fields; every problem carries a {@code timestamp} extension for log
 * correlation.
 *
 * <p>4xx outcomes log at WARN; the catch-all 500 logs at ERROR with stack trace.
 */
// PMD.TooManyMethods: one handler per exception type is inherent to an exception-mapping advice.
@SuppressWarnings("PMD.TooManyMethods")
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TIMESTAMP = "timestamp";

    private static final String ERRORS = "errors";

    private static final String MALFORMED_BODY = "Malformed request body";

    private static final String INVALID_PARAMETER = "Invalid request parameter";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(final ResourceNotFoundException exception) {
        LOG.warn("Resource not found: {}", LogSanitizer.forLog(messageOf(exception, "")));
        return problem(HttpStatus.NOT_FOUND, messageOf(exception, "Resource not found"));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(final ForbiddenException exception) {
        LOG.warn("Forbidden: {}", LogSanitizer.forLog(messageOf(exception, "")));
        return problem(HttpStatus.FORBIDDEN, messageOf(exception, "Forbidden"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(final MethodArgumentNotValidException exception) {
        final List<Map<String, String>> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", Objects.requireNonNullElse(error.getDefaultMessage(), "")))
                .toList();

        LOG.warn("Validation failed: {} field errors", fieldErrors.size());

        final ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty(ERRORS, fieldErrors);
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(final BusinessRuleException exception) {
        LOG.warn("Business rule violation: {}", LogSanitizer.forLog(messageOf(exception, "")));
        return problem(HttpStatus.CONFLICT, messageOf(exception, "Business rule violation"));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(final InvalidRequestException exception) {
        LOG.warn("Invalid request: {}", LogSanitizer.forLog(messageOf(exception, "")));
        return problem(HttpStatus.BAD_REQUEST, messageOf(exception, "Invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(final HttpMessageNotReadableException exception) {
        LOG.warn(MALFORMED_BODY);
        return problem(HttpStatus.BAD_REQUEST, MALFORMED_BODY);
    }

    /**
     * Maps a request parameter that violates a {@code @Min}/{@code @Max} bound (for example a
     * {@code limit=0} that would divide by zero in paging) to {@code 400}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParameterValidation(final ConstraintViolationException exception) {
        LOG.warn("Request parameter validation failed");
        return problem(HttpStatus.BAD_REQUEST, INVALID_PARAMETER);
    }

    /**
     * Maps a query or path parameter that does not convert to its target type to {@code 400}.
     *
     * <p>An unparseable enum or number in the request (for example {@code ?status=UNKNOWN}) is a
     * client error. Without this handler it falls to the catch-all {@code 500}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(final MethodArgumentTypeMismatchException exception) {
        LOG.warn("Parameter type mismatch: name={}", LogSanitizer.forLog(exception.getName()));
        return problem(HttpStatus.BAD_REQUEST, INVALID_PARAMETER);
    }

    /**
     * Maps an unsupported HTTP method to {@code 405} with an {@code Allow} header.
     *
     * <p>Without this handler the default falls through to the catch-all {@code 500}, turning a
     * client error into a server error in the 5xx metrics.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
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
        return builder.body(problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"));
    }

    /**
     * Maps an unsupported {@code Content-Type} to {@code 415}.
     *
     * <p>Without this handler the default falls through to {@code 500}, turning a client error into
     * a server error.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(final HttpMediaTypeNotSupportedException exception) {
        final MediaType received = exception.getContentType();
        LOG.warn("Unsupported content type: contentType={}",
                LogSanitizer.forLog(received == null ? "" : received.toString()));
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(final BadCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    /**
     * Lets an SSE stream that times out close quietly. The response is already committed as
     * {@code text/event-stream}, so serializing an error body onto it would fail.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncTimeout(final AsyncRequestTimeoutException exception) {
        LOG.debug("async stream timed out, closing quietly");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(final Exception exception) {
        LOG.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ProblemDetail problem(final HttpStatus status, final String detail) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty(TIMESTAMP, Instant.now());
        return problem;
    }

    private static String messageOf(final Exception exception, final String fallback) {
        return Objects.requireNonNullElse(exception.getMessage(), fallback);
    }
}
