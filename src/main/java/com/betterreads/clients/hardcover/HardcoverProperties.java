package com.betterreads.clients.hardcover;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Hardcover GraphQL API config bound from {@code hardcover.*}.
 *
 * <p>{@code bearerToken} is the raw token without the {@code Bearer } prefix. It defaults to empty so
 * the app starts without a token. {@code toString} masks the token to keep it out of logs.
 *
 * @param baseUrl GraphQL endpoint, e.g. {@code https://api.hardcover.app/v1/graphql}
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
