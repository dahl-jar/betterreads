package com.betterreads.common.web;

import com.betterreads.common.dto.ApiResponse;
import com.betterreads.common.dto.Paged;
import com.betterreads.common.dto.ResponseMeta;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every JSON response body in the {@link ApiResponse} envelope so clients see one consistent
 * shape. A {@link Paged} body becomes {@code {data: [...], meta: {...}}}; anything else becomes
 * {@code {data: ...}}.
 *
 * <p>Left untouched: an already-wrapped {@link ApiResponse}, a {@link ProblemDetail} error body
 * (RFC 9457 uses {@code application/problem+json}), a {@code text/event-stream} SSE body, the health
 * check (an infrastructure contract that probes read directly), and a null body (a 204).
 */
@ControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final String APPLICATION_PACKAGE = "com.betterreads";

    /** Bodies the envelope leaves alone; wrapping any of them would break its converter or contract. */
    private static final List<Class<?>> UNWRAPPED = List.of(
        ApiResponse.class, ProblemDetail.class, HealthResponse.class,
        CharSequence.class, byte[].class, Resource.class);

    @Override
    public boolean supports(
        final MethodParameter returnType,
        final Class<? extends HttpMessageConverter<?>> converterType) {
        final Class<?> declaringClass = returnType.getContainingClass();
        return declaringClass.getName().startsWith(APPLICATION_PACKAGE);
    }

    // PMD.ExcessiveParameterList: the six parameters are fixed by the ResponseBodyAdvice signature.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Override
    public @Nullable Object beforeBodyWrite(
        final @Nullable Object body,
        final MethodParameter returnType,
        final MediaType selectedContentType,
        final Class<? extends HttpMessageConverter<?>> selectedConverterType,
        final ServerHttpRequest request,
        final ServerHttpResponse response) {
        if (body == null || passesThrough(body, selectedContentType)) {
            return body;
        }
        if (body instanceof Paged<?> paged) {
            return ApiResponse.of(
                paged.items(), new ResponseMeta(paged.total(), paged.offset(), paged.limit()));
        }
        return ApiResponse.of(body);
    }

    private static boolean passesThrough(final Object body, final MediaType contentType) {
        return MediaType.TEXT_EVENT_STREAM.includes(contentType)
            || UNWRAPPED.stream().anyMatch(type -> type.isInstance(body));
    }
}
