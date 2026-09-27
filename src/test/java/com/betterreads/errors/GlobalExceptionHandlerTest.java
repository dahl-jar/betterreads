package com.betterreads.errors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class GlobalExceptionHandlerTest {

    private static final String BOOK_NOT_FOUND = "Book not found";

    private static final String BUSINESS_RULE_MESSAGE = "User already reviewed this book";

    private static final String UNEXPECTED_ERROR = "database connection lost";

    private static final String BINDING_TARGET = "request";

    private static final String ERRORS = "errors";

    private static final String FIELD = "field";

    private static final String MESSAGE = "message";

    private static final String EMAIL = "email";

    private static final String PASSWORD = "password";

    private static final String BLANK = "must not be blank";

    private static final String TOO_SHORT = "must be at least 8 characters";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    class WhenResourceNotFound {

        @Test
        void shouldReturn404WithExceptionMessage() {
            final ProblemDetail problem = handler.handleNotFound(
                    new ResourceNotFoundException(BOOK_NOT_FOUND));

            assertThat(problem)
                    .extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
                    .containsExactly(HttpStatus.NOT_FOUND.value(), BOOK_NOT_FOUND);
        }

        @Test
        void shouldFallBackToDefaultDetailWhenMessageIsNull() {
            final ProblemDetail problem = handler.handleNotFound(new ResourceNotFoundException(null));

            assertThat(problem)
                    .extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
                    .containsExactly(HttpStatus.NOT_FOUND.value(), "Resource not found");
        }
    }

    @Nested
    class WhenValidationFails {

        @Test
        void shouldReturn400ListingEveryFieldError() throws NoSuchMethodException {
            final ProblemDetail problem = handler.handleValidation(buildValidationException());

            assertThat(problem)
                    .satisfies(p -> assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()))
                    .extracting(ProblemDetail::getProperties, as(MAP))
                    .extractingByKey(ERRORS, as(LIST))
                    .containsExactly(
                            Map.of(FIELD, EMAIL, MESSAGE, BLANK),
                            Map.of(FIELD, PASSWORD, MESSAGE, TOO_SHORT));
        }
    }

    @Nested
    class WhenForbidden {

        @Test
        void statusIs403() {
            final ProblemDetail problem = handler.handleForbidden(new ForbiddenException("not yours"));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Nested
    class WhenBusinessRuleViolated {

        @Test
        void statusIs409() {
            final ProblemDetail problem = handler.handleBusinessRule(
                    new BusinessRuleException(BUSINESS_RULE_MESSAGE));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }
    }

    @Nested
    class WhenUnexpectedErrorOccurs {

        @Test
        void shouldReturn500WithoutInternals() {
            final ProblemDetail problem = handler.handleUnexpected(new RuntimeException(UNEXPECTED_ERROR));

            assertThat(problem)
                    .extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
                    .containsExactly(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred");
        }
    }

    @Nested
    class WhenMethodNotAllowed {

        @Test
        void shouldReturn405WithAllowHeader() {
            final ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupported(
                    new HttpRequestMethodNotSupportedException(HttpMethod.GET.name(), List.of(HttpMethod.POST.name())));

            assertThat(response)
                    .satisfies(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED))
                    .extracting(r -> r.getHeaders().get(HttpHeaders.ALLOW), as(LIST))
                    .contains(HttpMethod.POST.name());
        }

        @Test
        void shouldOmitAllowHeaderWhenNoMethodsSupported() {
            final ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupported(
                    new HttpRequestMethodNotSupportedException(HttpMethod.GET.name()));

            assertThat(response)
                    .satisfies(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED))
                    .extracting(r -> r.getHeaders().get(HttpHeaders.ALLOW))
                    .isNull();
        }
    }

    @Nested
    class WhenContentTypeNotSupported {

        @Test
        void statusIs415() {
            final ProblemDetail problem = handler.handleMediaTypeNotSupported(
                    new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        }
    }

    private MethodArgumentNotValidException buildValidationException() throws NoSuchMethodException {
        final BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), BINDING_TARGET);
        bindingResult.addError(new FieldError(BINDING_TARGET, EMAIL, BLANK));
        bindingResult.addError(new FieldError(BINDING_TARGET, PASSWORD, TOO_SHORT));

        final MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("buildValidationException"), -1);
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }
}
