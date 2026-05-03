package com.betterreads.operations.access;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Access JWT validation configuration. Bound from {@code cloudflare.access.*} keys.
 *
 * <p>Both fields are optional. When either is blank, the management chain skips JWT validation
 * and falls back to the {@code permitAll} default — appropriate for local dev where Cloudflare
 * Access isn't in front of the management port. Production sets both to enforce validation.
 *
 * @param aud the Application Audience tag from the Cloudflare Access app, blank to disable
 * @param teamDomain the Cloudflare Zero Trust team domain (e.g. {@code mydomain.cloudflareaccess.com}),
 *                   blank to disable; the JWKS URL is derived as
 *                   {@code https://<teamDomain>/cdn-cgi/access/certs}
 */
@ConfigurationProperties(prefix = "cloudflare.access")
public record CloudflareAccessProperties(
    @Nullable String aud,
    @Nullable String teamDomain
) {

    /**
     * Returns true when both {@code aud} and {@code teamDomain} are non-blank, meaning JWT
     * validation should be enforced.
     */
    public boolean isEnabled() {
        return aud != null && !aud.isBlank()
            && teamDomain != null && !teamDomain.isBlank();
    }

    /**
     * Returns the JWKS URL Cloudflare publishes for this team domain. Only call when
     * {@link #isEnabled()} returns true.
     */
    public String jwkSetUri() {
        return "https://" + teamDomain + "/cdn-cgi/access/certs";
    }
}
