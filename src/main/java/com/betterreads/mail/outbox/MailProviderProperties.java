package com.betterreads.mail.outbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mail-provider configuration bound from {@code mail.*}.
 *
 * <p>Provider-specific fields are optional at bind time so the app boots in {@code logging}
 * mode without them. The {@code requireXxx} methods throw at boot if a required Resend value
 * is missing, so the app fails to start rather than failing on the first send.
 *
 * @param provider {@code resend} or {@code logging}
 * @param apiKey Resend API key
 * @param from verified Resend sender address
 * @param appBaseUrl public origin used to build links
 */
@Validated
@ConfigurationProperties(prefix = "mail")
public record MailProviderProperties(
    @NotBlank @Pattern(regexp = "resend|logging") String provider,
    @Nullable String apiKey,
    @Nullable String from,
    @Nullable String appBaseUrl
) {

    @NotNull
    public String requireApiKey() {
        return require(apiKey, "RESEND_API_KEY");
    }

    @NotNull
    public String requireFrom() {
        return require(from, "MAIL_FROM");
    }

    /**
     * Returns the app base URL without a trailing slash. Throws when {@code appBaseUrl} is
     * blank or does not start with {@code http://} or {@code https://}.
     */
    @NotNull
    public String requireAppBaseUrl() {
        final String raw = require(appBaseUrl, "APP_BASE_URL");
        final String trimmed = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalStateException("APP_BASE_URL must start with http:// or https://");
        }
        return trimmed;
    }

    private static String require(@Nullable final String value, final String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " must be set when mail.provider=resend");
        }
        return value;
    }
}
