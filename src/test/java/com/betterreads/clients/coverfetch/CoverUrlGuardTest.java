package com.betterreads.clients.coverfetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoverUrlGuardTest {

    private static final byte[] PUBLIC_IP = {(byte) 203, 0, 113, 10};

    private final CoverUrlGuard guard = new CoverUrlGuard();

    private static CoverUrlGuard publicDns() throws UnknownHostException {
        final InetAddress publicAddress = InetAddress.getByAddress(PUBLIC_IP);
        return new CoverUrlGuard(host -> new InetAddress[] {publicAddress});
    }

    @Test
    @DisplayName("a public https cover url is allowed")
    void allowsPublicHttps() throws UnknownHostException {
        final CoverUrlGuard publicDns = publicDns();

        assertThat(publicDns.isAllowed("https://covers.example.test/b/id/1-L.jpg")).isTrue();
    }

    @Test
    void shouldRejectUnresolvedHost() {
        final CoverUrlGuard failingDns = new CoverUrlGuard(host -> {
            throw new UnknownHostException(host);
        });

        final boolean allowed = failingDns.isAllowed("https://covers.invalid/cover.jpg");

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("a malformed url is rejected")
    void rejectsMalformed() {
        assertThat(guard.isAllowed("not a url")).isFalse();
    }

    @Nested
    @DisplayName("url shape")
    class UrlShape {

        @Test
        @DisplayName("a non-http scheme is rejected")
        void rejectsNonHttpScheme() throws UnknownHostException {
            final CoverUrlGuard publicDns = publicDns();

            assertThat(publicDns.isAllowed("gopher://covers.example.test/")).isFalse();
        }

        @Test
        @DisplayName("should reject a url without a host")
        void shouldRejectUrlWithoutHost() throws UnknownHostException {
            final CoverUrlGuard publicDns = publicDns();

            assertThat(publicDns.isAllowed("http:///cover.jpg")).isFalse();
        }

        @Test
        @DisplayName("should reject a url without a scheme")
        void shouldRejectUrlWithoutScheme() throws UnknownHostException {
            final CoverUrlGuard publicDns = publicDns();

            assertThat(publicDns.isAllowed("//covers.example.test/cover.jpg")).isFalse();
        }
    }

    @Nested
    @DisplayName("address ranges")
    class AddressRanges {

        @Test
        @DisplayName("a loopback address is rejected")
        void rejectsLoopback() {
            assertThat(guard.isAllowed("http://127.0.0.1/cover.jpg")).isFalse();
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
        }

        @Test
        @DisplayName("a carrier-grade NAT address is rejected")
        void rejectsCarrierGradeNat() {
            assertThat(guard.isAllowed("http://100.64.0.1/cover.jpg")).isFalse();
            assertThat(guard.isAllowed("http://100.127.255.254/cover.jpg")).isFalse();
        }

        @Test
        @DisplayName("should allow an address just past the carrier-grade NAT range")
        void shouldAllowAddressPastCarrierGradeNat() {
            assertThat(guard.isAllowed("http://100.128.0.1/cover.jpg")).isTrue();
        }

        @Test
        @DisplayName("the wildcard address is rejected")
        void rejectsWildcardAddress() {
            assertThat(guard.isAllowed("http://0.0.0.0/cover.jpg")).isFalse();
        }

        @Test
        @DisplayName("a multicast address is rejected")
        void rejectsMulticast() {
            assertThat(guard.isAllowed("http://224.0.0.1/cover.jpg")).isFalse();
        }

        @Test
        @DisplayName("an IPv6 unique-local address is rejected")
        void rejectsIpv6UniqueLocal() {
            assertThat(guard.isAllowed("http://[fc00::1]/cover.jpg")).isFalse();
            assertThat(guard.isAllowed("http://[fd12:3456:789a::1]/cover.jpg")).isFalse();
        }

        @Test
        @DisplayName("should allow an IPv6 address outside the unique-local range")
        void shouldAllowIpv6OutsideUniqueLocal() {
            assertThat(guard.isAllowed("http://[2001:db8::1]/cover.jpg")).isTrue();
        }
    }
}
