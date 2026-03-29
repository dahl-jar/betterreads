package com.betterreads.common.exception;

import com.betterreads.common.dto.ApiErrorResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String BOOK_NOT_FOUND = "Book not found";

    private static final String BUSINESS_RULE_MESSAGE = "User already reviewed this book";

    private static final String UNEXPECTED_ERROR = "database connection lost";

    private static final String BINDING_TARGET = "request";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    class WhenResourceNotFound {

        @Test
        void returnsNotFoundStatus() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                    new ResourceNotFoundException(BOOK_NOT_FOUND));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void returnsExceptionMessageInBody() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                    new ResourceNotFoundException(BOOK_NOT_FOUND));

            assertThat(response.getBody()).extracting(ApiErrorResponse::message).isEqualTo(BOOK_NOT_FOUND);
        }

        @Test
        void returnsEmptyFieldErrors() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                    new ResourceNotFoundException(BOOK_NOT_FOUND));

            final ApiErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.fieldErrors()).isEmpty();
        }
    }

    @Nested
    class WhenValidationFails {

        @Test
        void returnsBadRequestStatus() throws NoSuchMethodException {
            final ResponseEntity<ApiErrorResponse> response = handler.handleValidation(buildValidationException());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returnsAllFieldErrors() throws NoSuchMethodException {
            final ResponseEntity<ApiErrorResponse> response = handler.handleValidation(buildValidationException());

            final ApiErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.fieldErrors()).hasSize(2);
        }
    }

    @Nested
    class WhenBusinessRuleViolated {

        @Test
        void returnsConflictStatus() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleBusinessRule(
                    new BusinessRuleException(BUSINESS_RULE_MESSAGE));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void returnsExceptionMessageInBody() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleBusinessRule(
                    new BusinessRuleException(BUSINESS_RULE_MESSAGE));

            assertThat(response.getBody()).extracting(ApiErrorResponse::message).isEqualTo(BUSINESS_RULE_MESSAGE);
        }
    }

    @Nested
    class WhenUnexpectedErrorOccurs {

        @Test
        void returnsInternalServerErrorStatus() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                    new RuntimeException(UNEXPECTED_ERROR));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void returnsGenericMessageWithoutExposingDetails() {
            final ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                    new RuntimeException(UNEXPECTED_ERROR));

            assertThat(response.getBody()).extracting(ApiErrorResponse::message)
                    .isEqualTo("An unexpected error occurred");
        }
    }

    private MethodArgumentNotValidException buildValidationException() throws NoSuchMethodException {
        final BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), BINDING_TARGET);
        bindingResult.addError(new FieldError(BINDING_TARGET, "email", "must not be blank"));
        bindingResult.addError(new FieldError(BINDING_TARGET, "password", "must be at least 8 characters"));

        final MethodParameter methodParameter = new MethodParameter(
                WhenValidationFails.class.getDeclaredMethod(
                        "returnsBadRequestStatus"), -1);
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }
}
