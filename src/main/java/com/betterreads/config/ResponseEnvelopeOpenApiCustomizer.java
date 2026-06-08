package com.betterreads.config;

import com.betterreads.common.dto.ResponseMeta;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rewrites every documented 2xx JSON body to the runtime {@code ApiResponse} envelope, which the
 * {@code ApiResponseBodyAdvice} adds after springdoc reads the controller return types. A paged
 * response documents {@code data} as the item array plus {@code meta}; any other response documents
 * {@code data} as the original schema.
 */
@Configuration
public class ResponseEnvelopeOpenApiCustomizer {

    private static final String META_REF = "#/components/schemas/ResponseMeta";

    private static final String DATA = "data";

    private static final Map<String, String> PAGED_ITEM_BY_TYPE = Map.of(
        "CommentPage", "CommentResponse",
        "ReviewPage", "ReviewResponse",
        "BookSearchResult", "BookSearchDocument");

    private static final Set<String> NOT_WRAPPED = Set.of("HealthResponse");

    private static final String EVENT_STREAM = "text/event-stream";

    @Bean
    OpenApiCustomizer responseEnvelopeCustomizer() {
        return openApi -> {
            registerResponseMeta(openApi);
            openApi.getPaths().values().forEach(path ->
                path.readOperations().forEach(ResponseEnvelopeOpenApiCustomizer::wrapSuccessResponses));
        };
    }

    private static void registerResponseMeta(final io.swagger.v3.oas.models.OpenAPI openApi) {
        ModelConverters.getInstance().readAll(ResponseMeta.class)
            .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    }

    private static void wrapSuccessResponses(final Operation operation) {
        operation.getResponses().forEach((status, response) -> {
            if (status.startsWith("2")) {
                wrap(response);
            }
        });
    }

    private static void wrap(final ApiResponse response) {
        if (response.getContent() == null) {
            return;
        }
        response.getContent().forEach((contentType, media) -> {
            final Schema<?> original = media.getSchema();
            if (original != null
                && !EVENT_STREAM.equals(contentType)
                && !NOT_WRAPPED.contains(refName(original))) {
                media.setSchema(envelope(original));
            }
        });
    }

    private static Schema<Object> envelope(final Schema<?> original) {
        final ObjectSchema wrapper = new ObjectSchema();
        final String item = PAGED_ITEM_BY_TYPE.get(refName(original));
        if (item == null) {
            wrapper.addProperty(DATA, original);
            return wrapper;
        }
        wrapper.addProperty(DATA,
            new ArraySchema().items(new Schema<>().$ref("#/components/schemas/" + item)));
        wrapper.addProperty("meta", new Schema<>().$ref(META_REF));
        return wrapper;
    }

    private static String refName(final Schema<?> schema) {
        final String ref = schema.get$ref();
        return ref == null ? "" : ref.substring(ref.lastIndexOf('/') + 1);
    }
}
