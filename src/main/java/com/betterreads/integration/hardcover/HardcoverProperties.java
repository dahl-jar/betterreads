package com.betterreads.integration.hardcover;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Hardcover GraphQL API config bound from {@code hardcover.*}.
 *
 * <p>{@code bearerToken} is the raw token; {@link HardcoverWebClientConfig} prepends {@code Bearer }.
 * It defaults to empty so the context starts without a token, in which case the client returns
 * empty. {@code toString} masks the token so it never reaches a log line.
 *
 * @param baseUrl GraphQL endpoint, e.g. {@code https://api.hardcover.app/v1/graphql}
 * @param bearerToken user-scoped API token, time-limited and regenerated at hardcover.app
 * @param connectTimeout TCP connect timeout in milliseconds
 * @param readTimeout per-response read timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "hardcover")
public record HardcoverProperties(
    @NotBlank String baseUrl,
    String bearerToken,
    @Positive int connectTimeout,
    @Positive int readTimeout
) {

    @Override
    public String toString() {
        return "HardcoverProperties[baseUrl=" + baseUrl
            + ", bearerToken=***, connectTimeout=" + connectTimeout
            + ", readTimeout=" + readTimeout + ']';
    }
}
