package com.betterreads.web;

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

/** a Paged body becomes {@code {data: [...], meta: {...}}}, any other JSON body {@code {data: ...}} */
@ControllerAdvice
class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final String APPLICATION_PACKAGE = "com.betterreads";

    /** wrapping these breaks their converter, and probes read the health body as-is */
    private static final List<Class<?>> UNWRAPPED = List.of(
        ApiResponse.class, ProblemDetail.class, HealthResponse.class,
        CharSequence.class, byte[].class, Resource.class);

    @Override
    public boolean supports(
        final MethodParameter returnType,
        final Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getContainingClass().getName().startsWith(APPLICATION_PACKAGE);
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
            return new ApiResponse<>(
                paged.items(), new ResponseMeta(paged.total(), paged.offset(), paged.limit()));
        }
        return new ApiResponse<>(body, null);
    }

    private static boolean passesThrough(final Object body, final MediaType contentType) {
        return MediaType.TEXT_EVENT_STREAM.includes(contentType)
            || UNWRAPPED.stream().anyMatch(type -> type.isInstance(body));
    }
}
