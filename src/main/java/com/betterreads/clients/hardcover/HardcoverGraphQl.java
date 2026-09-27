package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.clients.http.WebClients;
import com.betterreads.logging.LogSanitizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Sends Hardcover GraphQL requests and parses Hardcover ids. */
public final class HardcoverGraphQl {

    private HardcoverGraphQl() {
    }

    public static <T> Optional<T> post(
        final WebClient client,
        final Logger log,
        final HardcoverGraphQlRequest request,
        final ParameterizedTypeReference<T> type,
        final String logQuery
    ) {
        return WebClients.emptyOn4xx(() -> Optional.ofNullable(client.post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(type)
            .doOnError(WebClientResponseException.Unauthorized.class, exception -> log.warn(
                "hardcover.auth token rejected (401), expired or revoked, regenerate at "
                    + "hardcover.app query={}", LogSanitizer.forLog(logQuery)))
            .block()), log, "hardcover.request");
    }

    public static <D> List<D> search(
        final WebClient client,
        final Logger log,
        final String searchQuery,
        final String query,
        final ParameterizedTypeReference<TypesenseSearchResponse<D>> type
    ) {
        return post(client, log, new HardcoverGraphQlRequest(searchQuery, Map.of("q", query)), type, query)
            .map(TypesenseHits::documents)
            .orElseGet(List::of);
    }

    /** Returns the id as an int, or empty when it is absent or not numeric. */
    public static Optional<Integer> parseId(final @Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(id));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
