package com.betterreads.clients.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicUrlGuardTest {

    private static final byte[] PUBLIC_IP = {(byte) 203, 0, 113, 10};

    private final PublicUrlGuard guard = new PublicUrlGuard();

    private static PublicUrlGuard publicDns() throws UnknownHostException {
        final InetAddress publicAddress = InetAddress.getByAddress(PUBLIC_IP);
        return new PublicUrlGuard(host -> new InetAddress[] {publicAddress});
    }

    @Test
    @DisplayName("a public https cover url is allowed")
    void allowsPublicHttps() throws UnknownHostException {
        final PublicUrlGuard publicDns = publicDns();

        final boolean allowed = publicDns.isAllowed("https://covers.example.test/b/id/1-L.jpg");

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectUnresolvedHost() {
        final PublicUrlGuard failingDns = new PublicUrlGuard(host -> {
            throw new UnknownHostException(host);
        });

        final boolean allowed = failingDns.isAllowed("https://covers.invalid/cover.jpg");

        assertThat(allowed).isFalse();
    }

    @Test
    void shouldRejectAHostWithNoAddresses() {
        final PublicUrlGuard emptyDns = new PublicUrlGuard(host -> new InetAddress[0]);

        final boolean allowed = emptyDns.isAllowed("https://covers.example.test/cover.jpg");

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("a malformed url is rejected")
    void rejectsMalformed() {
        final boolean allowed = guard.isAllowed("not a url");

        assertThat(allowed).isFalse();
    }

    @Nested
    @DisplayName("url shape")
    class UrlShape {

        @ParameterizedTest
        @ValueSource(strings = {"gopher://covers.example.test/", "http:///cover.jpg", "//covers.example.test/cover.jpg"})
        @DisplayName("a url without an http(s) scheme or a host is rejected")
        void shouldRejectAUrlWithoutAnHttpSchemeOrHost(final String url) throws UnknownHostException {
            final PublicUrlGuard publicDns = publicDns();

            final boolean allowed = publicDns.isAllowed(url);

            assertThat(allowed).isFalse();
        }
    }

    @Nested
    @DisplayName("address ranges")
    class AddressRanges {

        @ParameterizedTest
        @ValueSource(strings = {
            "http://127.0.0.1/cover.jpg",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.1.2.3:8080/cover.jpg",
            "http://100.64.0.1/cover.jpg",
            "http://100.127.255.254/cover.jpg",
            "http://0.0.0.0/cover.jpg",
            "http://224.0.0.1/cover.jpg",
            "http://[fc00::1]/cover.jpg",
            "http://[fd12:3456:789a::1]/cover.jpg"})
        @DisplayName("a loopback, link-local, private, carrier-grade NAT, wildcard, multicast or unique-local address"
            + " is rejected")
        void shouldRejectANonPublicAddress(final String url) {
            final boolean allowed = guard.isAllowed(url);

            assertThat(allowed).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"http://100.128.0.1/cover.jpg", "http://[2001:db8::1]/cover.jpg"})
        @DisplayName("should allow an address just outside the carrier-grade NAT and unique-local ranges")
        void shouldAllowAnAddressPastTheNonPublicRanges(final String url) {
            final boolean allowed = guard.isAllowed(url);

            assertThat(allowed).isTrue();
        }
    }
}
