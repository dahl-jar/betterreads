package com.betterreads.integration.hardcover.client;

import com.betterreads.common.util.LogSanitizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Shared Hardcover GraphQL handling: id parsing and 4xx-versus-5xx recovery. */
final class HardcoverGraphQl {

    private HardcoverGraphQl() {
    }

    /** Returns the id as an int, or null when it is absent or not numeric. */
    static @Nullable Integer parseId(final @Nullable String id) {
        if (id == null) {
            return null;
        }
        try {
            return Integer.valueOf(id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Logs and swallows 4xx, calling out 401 as a rejected token; rethrows everything else so 5xx
     * propagates.
     */
    static void recoverOrThrow(
        final Logger log,
        final WebClientResponseException exception,
        final String query
    ) {
        if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            log.warn("hardcover.auth token rejected (401), expired or revoked, regenerate at "
                + "hardcover.app query={}", LogSanitizer.forLog(query));
            return;
        }
        if (exception.getStatusCode().is4xxClientError()) {
            log.debug("hardcover.request returned 4xx status={}", exception.getStatusCode().value());
            return;
        }
        throw exception;
    }
}
