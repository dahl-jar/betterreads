package com.betterreads.ratelimit;

import static com.betterreads.ratelimit.RateLimitFixtures.CF_CONNECTING_IP_HEADER;
import static com.betterreads.ratelimit.RateLimitFixtures.CLIENT_A;
import static com.betterreads.ratelimit.RateLimitFixtures.CLIENT_B;
import static com.betterreads.ratelimit.RateLimitFixtures.PROXY;
import static com.betterreads.ratelimit.RateLimitFixtures.PROXY_CIDR;
import static com.betterreads.ratelimit.RateLimitFixtures.XFF_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final String MALFORMED = "not-an-ip";

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("should fall back to remote address if CF-Connecting-IP is malformed",
                MALFORMED, null, List.of(), PROXY),
            Arguments.of("should key on first X-Forwarded-For entry from trusted proxy",
                null, CLIENT_A + ", " + CLIENT_B, List.of(PROXY_CIDR), CLIENT_A),
            Arguments.of("should fall back to remote address if X-Forwarded-For is malformed",
                null, MALFORMED, List.of(PROXY_CIDR), PROXY),
            Arguments.of("should skip malformed trusted-proxy CIDR",
                null, CLIENT_A, List.of("not-a-cidr", PROXY_CIDR), CLIENT_A)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void shouldResolveClientIp(
        final String description,
        @Nullable final String cfConnectingIp,
        @Nullable final String forwardedFor,
        final List<String> trustedProxies,
        final String expected
    ) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        if (cfConnectingIp != null) {
            request.addHeader(CF_CONNECTING_IP_HEADER, cfConnectingIp);
        }
        if (forwardedFor != null) {
            request.addHeader(XFF_HEADER, forwardedFor);
        }
        final ClientIpResolver resolver = new ClientIpResolver(trustedProxies);

        final String clientIp = resolver.clientIp(request);

        assertThat(clientIp).as(description).isEqualTo(expected);
    }
}
