package com.betterreads.operations.access;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Access JWT config bound from {@code cloudflare.access.*}.
 *
 * <p>Both fields are optional. Leaving either blank turns the check off and the actuator
 * endpoints fall back to {@code permitAll}.
 *
 * @param aud Application Audience tag, blank to disable
 * @param teamDomain Zero Trust team domain, e.g. {@code mydomain.cloudflareaccess.com}
 */
@ConfigurationProperties(prefix = "cloudflare.access")
public record CloudflareAccessProperties(
    @Nullable String aud,
    @Nullable String teamDomain
) {

    /** Returns true when both {@code aud} and {@code teamDomain} are set. */
    public boolean isEnabled() {
        return aud != null && !aud.isBlank()
            && teamDomain != null && !teamDomain.isBlank();
    }

    /**
     * Returns the JWKS URL Cloudflare publishes for this team domain. Only valid when
     * {@link #isEnabled()}.
     */
    public String jwkSetUri() {
        return "https://" + teamDomain + "/cdn-cgi/access/certs";
    }
}
