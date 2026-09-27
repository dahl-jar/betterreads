package com.betterreads.clients.mail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mail settings bound from {@code mail.*}.
 *
 * <p>The Resend fields are optional at bind time so the app boots in {@code logging} mode
 * without them.
 *
 * @param from verified Resend sender address
 * @param appBaseUrl public origin for links in mail bodies
 */
@Validated
@ConfigurationProperties(prefix = "mail")
public record MailProviderProperties(
    @NotBlank @Pattern(regexp = "resend|logging") String provider,
    @Nullable String apiKey,
    @Nullable String from,
    @Nullable String appBaseUrl
) {

    public String requireApiKey() {
        return require(apiKey, "RESEND_API_KEY");
    }

    public String requireFrom() {
        return require(from, "MAIL_FROM");
    }

    /** Templates append a path starting with a slash, so a trailing slash is dropped. */
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
