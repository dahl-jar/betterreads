package com.betterreads.common.exception;

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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

class GlobalExceptionHandlerTest {

    private static final String BOOK_NOT_FOUND = "Book not found";

    private static final String BUSINESS_RULE_MESSAGE = "User already reviewed this book";

    private static final String UNEXPECTED_ERROR = "database connection lost";

    private static final String BINDING_TARGET = "request";

    private static final String ERRORS = "errors";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    class WhenResourceNotFound {

        @Test
        void statusDrivesNotFound() {
            final ProblemDetail problem = handler.handleNotFound(
                    new ResourceNotFoundException(BOOK_NOT_FOUND));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @Test
        void detailCarriesTheExceptionMessage() {
            final ProblemDetail problem = handler.handleNotFound(
                    new ResourceNotFoundException(BOOK_NOT_FOUND));

            assertThat(problem.getDetail()).isEqualTo(BOOK_NOT_FOUND);
        }
    }

    @Nested
    class WhenValidationFails {

        @Test
        void statusDrivesBadRequest() throws NoSuchMethodException {
            final ProblemDetail problem = handler.handleValidation(buildValidationException());

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }

        @Test
        void errorsExtensionListsEveryFieldError() throws NoSuchMethodException {
            final ProblemDetail problem = handler.handleValidation(buildValidationException());

            assertThat(problem.getProperties())
                    .extractingByKey(ERRORS, as(LIST))
                    .hasSize(2);
        }
    }

    @Nested
    class WhenForbidden {

        @Test
        void statusDrivesForbidden() {
            final ProblemDetail problem = handler.handleForbidden(new ForbiddenException("not yours"));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Nested
    class WhenBusinessRuleViolated {

        @Test
        void statusDrivesConflict() {
            final ProblemDetail problem = handler.handleBusinessRule(
                    new BusinessRuleException(BUSINESS_RULE_MESSAGE));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }
    }

    @Nested
    class WhenUnexpectedErrorOccurs {

        @Test
        void statusDrivesInternalServerError() {
            final ProblemDetail problem = handler.handleUnexpected(new RuntimeException(UNEXPECTED_ERROR));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        @Test
        void detailDoesNotExposeInternals() {
            final ProblemDetail problem = handler.handleUnexpected(new RuntimeException(UNEXPECTED_ERROR));

            assertThat(problem.getDetail())
                    .isEqualTo("An unexpected error occurred")
                    .doesNotContain(UNEXPECTED_ERROR);
        }
    }

    @Nested
    class WhenMethodNotAllowed {

        @Test
        void statusDrives405() {
            final ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupported(
                    new HttpRequestMethodNotSupportedException(HttpMethod.GET.name(), List.of(HttpMethod.POST.name())));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }

        @Test
        void exposesAllowHeaderWithSupportedMethods() {
            final ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupported(
                    new HttpRequestMethodNotSupportedException(HttpMethod.GET.name(), List.of(HttpMethod.POST.name())));

            assertThat(response.getHeaders().get(HttpHeaders.ALLOW)).contains(HttpMethod.POST.name());
        }
    }

    @Nested
    class WhenContentTypeNotSupported {

        @Test
        void statusDrives415() {
            final ProblemDetail problem = handler.handleMediaTypeNotSupported(
                    new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        }
    }

    private MethodArgumentNotValidException buildValidationException() throws NoSuchMethodException {
        final BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), BINDING_TARGET);
        bindingResult.addError(new FieldError(BINDING_TARGET, "email", "must not be blank"));
        bindingResult.addError(new FieldError(BINDING_TARGET, "password", "must be at least 8 characters"));

        final MethodParameter methodParameter = new MethodParameter(
                WhenValidationFails.class.getDeclaredMethod(
                        "statusDrivesBadRequest"), -1);
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }
}
