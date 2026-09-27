package com.betterreads.web;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc documents the raw controller return types while the runtime wraps bodies in
 * {@code {data, meta}}, so 2xx JSON schemas get wrapped the same way.
 * a paged response gets {@code data} as the item array plus {@code meta}
 */
@Configuration
class ResponseEnvelopeOpenApiCustomizer {

    private static final String SCHEMA_REF = "#/components/schemas/";

    private static final String DATA = "data";

    private static final Map<String, String> PAGED_ITEM_BY_TYPE = Map.of(
        "CommentPage", "CommentResponse",
        "ReviewPage", "ReviewResponse",
        "BookSearchResult", "BookSearchDocument");

    private static final String HEALTH_SCHEMA = HealthResponse.class.getSimpleName();

    private static final String EVENT_STREAM = "text/event-stream";

    private static final String IMAGE_PREFIX = "image/";

    @Bean
    OpenApiCustomizer responseEnvelopeCustomizer() {
        return openApi -> {
            registerResponseMeta(openApi);
            openApi.getPaths().values().forEach(path ->
                path.readOperations().forEach(ResponseEnvelopeOpenApiCustomizer::wrapSuccessResponses));
        };
    }

    private static void registerResponseMeta(final OpenAPI openApi) {
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
                && !contentType.startsWith(IMAGE_PREFIX)
                && !HEALTH_SCHEMA.equals(refName(original))) {
                media.setSchema(wrapperSchema(original));
            }
        });
    }

    private static Schema<Object> wrapperSchema(final Schema<?> original) {
        final ObjectSchema wrapper = new ObjectSchema();
        final String item = PAGED_ITEM_BY_TYPE.get(refName(original));
        if (item == null) {
            wrapper.addProperty(DATA, original);
            return wrapper;
        }
        wrapper.addProperty(DATA,
            new ArraySchema().items(new Schema<>().$ref(SCHEMA_REF + item)));
        wrapper.addProperty("meta", new Schema<>().$ref(SCHEMA_REF + "ResponseMeta"));
        return wrapper;
    }

    private static String refName(final Schema<?> schema) {
        final String ref = schema.get$ref();
        return ref == null ? "" : ref.substring(ref.lastIndexOf('/') + 1);
    }
}
