package com.betterreads.errors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
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

    private static final String UNKNOWN_PATH = "/api/v1/search";

    private static final String BINDING_TARGET = "request";

    private static final String ERRORS = "errors";

    private static final String FIELD = "field";

    private static final String MESSAGE = "message";

    private static final String EMAIL = "email";

    private static final String PASSWORD = "password";

    private static final String BLANK = "must not be blank";

    private static final String TOO_SHORT = "must be at least 8 characters";

    private static final String STREAM_PATH = "/stream";

    private static final String UNEXPECTED_ERROR_LOG = "Unexpected error";

    private static final AsyncRequestNotUsableException CLOSED_CLIENT =
            new AsyncRequestNotUsableException("Broken pipe");

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
    class WhenNoRouteMatches {

        @Test
        void shouldReturn404ForAnUnknownPath() {
            final ProblemDetail problem = handler.handleNoResource(
                    new NoResourceFoundException(HttpMethod.GET, UNKNOWN_PATH, UNKNOWN_PATH));

            assertThat(problem)
                    .extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
                    .containsExactly(HttpStatus.NOT_FOUND.value(), "No endpoint matches this path");
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
        void shouldReturn403() {
            final ProblemDetail problem = handler.handleForbidden(new ForbiddenException("not yours"));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Nested
    class WhenBusinessRuleViolated {

        @Test
        void shouldReturn409() {
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
        void shouldReturn415() {
            final ProblemDetail problem = handler.handleMediaTypeNotSupported(
                    new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        }

        @Test
        void shouldReturn415WhenContentTypeIsInvalid() {
            final ProblemDetail problem = handler.handleMediaTypeNotSupported(
                    new HttpMediaTypeNotSupportedException("Invalid mime type"));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class WhenStreamFails {

        @Test
        void shouldNotLogErrorWhenClientClosedStream(final CapturedOutput output) throws Exception {
            final MockMvc mockMvc = failingMvc(CLOSED_CLIENT);

            mockMvc.perform(MockMvcRequestBuilders.get(STREAM_PATH));

            assertThat(output).doesNotContain(UNEXPECTED_ERROR_LOG);
        }

        @Test
        void shouldReturnNoContentWhenClientClosedStream() throws Exception {
            final MockMvc mockMvc = failingMvc(CLOSED_CLIENT);

            final ResultActions result = mockMvc.perform(MockMvcRequestBuilders.get(STREAM_PATH));

            result.andExpect(MockMvcResultMatchers.status().isNoContent());
        }

        @Test
        void shouldLogErrorWhenStreamFailsWithClientConnected(final CapturedOutput output) throws Exception {
            final MockMvc mockMvc = failingMvc(new IOException("disk full"));

            final ResultActions result = mockMvc.perform(MockMvcRequestBuilders.get(STREAM_PATH));

            result.andExpect(MockMvcResultMatchers.status().isInternalServerError());
            assertThat(output).contains(UNEXPECTED_ERROR_LOG);
        }

        private MockMvc failingMvc(final IOException failure) {
            final StandaloneMockMvcBuilder builder = MockMvcBuilders.standaloneSetup(new FailingStream(failure));
            builder.setControllerAdvice(new GlobalExceptionHandler());
            return builder.build();
        }

        @RestController
        static final class FailingStream {

            private final IOException failure;

            FailingStream(final IOException failure) {
                this.failure = failure;
            }

            @GetMapping(STREAM_PATH)
            void stream() throws IOException {
                throw failure;
            }
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
