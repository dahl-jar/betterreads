package com.betterreads.integration.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guard allows public http(s) cover URLs and rejects the targets an attacker-supplied URL could
 * point at: non-http schemes, missing hosts, and loopback, link-local, or private addresses.
 */
class CoverUrlGuardTest {

    private final CoverUrlGuard guard = new CoverUrlGuard();

    @Test
    @DisplayName("a public https cover url is allowed")
    void allowsPublicHttps() {
        assertThat(guard.isAllowed("https://covers.openlibrary.org/b/id/1-L.jpg")).isTrue();
    }

    @Test
    @DisplayName("a non-http scheme is rejected")
    void rejectsNonHttpScheme() {
        assertThat(guard.isAllowed("file:///etc/passwd")).isFalse();
        assertThat(guard.isAllowed("gopher://example.org/")).isFalse();
    }

    @Test
    @DisplayName("a loopback address is rejected")
    void rejectsLoopback() {
        assertThat(guard.isAllowed("http://127.0.0.1/cover.jpg")).isFalse();
        assertThat(guard.isAllowed("http://localhost:9000/cover.jpg")).isFalse();
    }

    @Test
    @DisplayName("a link-local metadata address is rejected")
    void rejectsLinkLocal() {
        assertThat(guard.isAllowed("http://169.254.169.254/latest/meta-data/")).isFalse();
    }

    @Test
    @DisplayName("a private-range address is rejected")
    void rejectsPrivateRange() {
        assertThat(guard.isAllowed("http://10.1.2.3:8080/cover.jpg")).isFalse();
        assertThat(guard.isAllowed("http://192.168.1.5/cover.jpg")).isFalse();
    }

    @Test
    @DisplayName("a carrier-grade NAT address is rejected")
    void rejectsCarrierGradeNat() {
        assertThat(guard.isAllowed("http://100.64.0.1/cover.jpg")).isFalse();
        assertThat(guard.isAllowed("http://100.127.255.254/cover.jpg")).isFalse();
    }

    @Test
    @DisplayName("an IPv6 unique-local address is rejected")
    void rejectsIpv6UniqueLocal() {
        assertThat(guard.isAllowed("http://[fc00::1]/cover.jpg")).isFalse();
        assertThat(guard.isAllowed("http://[fd12:3456:789a::1]/cover.jpg")).isFalse();
    }

    @Test
    @DisplayName("a malformed url is rejected")
    void rejectsMalformed() {
        assertThat(guard.isAllowed("not a url")).isFalse();
        assertThat(guard.isAllowed("https://")).isFalse();
    }
}
