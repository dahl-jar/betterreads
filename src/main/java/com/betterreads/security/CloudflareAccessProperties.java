package com.betterreads.security;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare Access settings bound from {@code cloudflare.access.*}. Leaving either field blank
 * turns the check off and leaves the actuator endpoints open.
 *
 * @param aud Application Audience tag
 * @param teamDomain Zero Trust team domain, e.g. {@code mydomain.cloudflareaccess.com}
 */
@ConfigurationProperties(prefix = "cloudflare.access")
record CloudflareAccessProperties(
    @Nullable String aud,
    @Nullable String teamDomain
) {

    public boolean isEnabled() {
        return aud != null && !aud.isBlank()
            && teamDomain != null && !teamDomain.isBlank();
    }

    /** only valid once {@code teamDomain} is set */
    public String jwkSetUri() {
        return "https://" + teamDomain + "/cdn-cgi/access/certs";
    }
}
