package com.betterreads.errors;

import jakarta.validation.ConstraintViolationException;

import com.betterreads.logging.LogSanitizer;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        return warnedProblem(HttpStatus.NOT_FOUND, "Resource not found", exception);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(final NoResourceFoundException exception) {
        LOG.debug("No endpoint for path={}", LogSanitizer.forLog(exception.getResourcePath()));
        return problem(HttpStatus.NOT_FOUND, "No endpoint matches this path");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(final ForbiddenException exception) {
        return warnedProblem(HttpStatus.FORBIDDEN, "Forbidden", exception);
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
        return warnedProblem(HttpStatus.CONFLICT, "Business rule violation", exception);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(final InvalidRequestException exception) {
        return warnedProblem(HttpStatus.BAD_REQUEST, "Invalid request", exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(final HttpMessageNotReadableException exception) {
        LOG.warn(MALFORMED_BODY);
        return problem(HttpStatus.BAD_REQUEST, MALFORMED_BODY);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParameterValidation(final ConstraintViolationException exception) {
        LOG.warn("Request parameter validation failed");
        return problem(HttpStatus.BAD_REQUEST, INVALID_PARAMETER);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(final MethodArgumentTypeMismatchException exception) {
        LOG.warn("Parameter type mismatch: name={}", LogSanitizer.forLog(exception.getName()));
        return problem(HttpStatus.BAD_REQUEST, INVALID_PARAMETER);
    }

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

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncTimeout(final AsyncRequestTimeoutException exception) {
        LOG.debug("async stream timed out, closing quietly");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleClientClosed(final AsyncRequestNotUsableException exception) {
        LOG.debug("client closed the stream: {}", LogSanitizer.forLog(messageOf(exception, "")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public @Nullable ProblemDetail handleFailedSend(final IllegalStateException exception) {
        if (DisconnectedClientHelper.isClientDisconnectedException(exception)) {
            LOG.debug("client closed the stream during the first send");
            return null;
        }
        return handleUnexpected(exception);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(final Exception exception) {
        LOG.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ProblemDetail warnedProblem(
            final HttpStatus status, final String label, final RuntimeException exception) {
        LOG.warn("{}: {}", LogSanitizer.forLog(label), LogSanitizer.forLog(messageOf(exception, "")));
        return problem(status, messageOf(exception, label));
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
