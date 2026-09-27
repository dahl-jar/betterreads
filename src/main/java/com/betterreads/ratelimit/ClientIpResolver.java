package com.betterreads.ratelimit;

import com.betterreads.logging.LogSanitizer;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

final class ClientIpResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ClientIpResolver.class);

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    private final List<IpAddressMatcher> trustedProxies;

    /**
     * A malformed trusted-proxy CIDR is logged and dropped, so one bad config line cannot break
     * startup.
     */
    ClientIpResolver(final List<String> trustedProxies) {
        this.trustedProxies = parseCidrs(trustedProxies);
    }

    /**
     * Prefers {@code CF-Connecting-IP}, which Cloudflare overwrites on every request so a caller
     * cannot forge it. Falls back to {@code X-Forwarded-For}, honored only when the immediate
     * client sits in a trusted-proxy CIDR.
     */
    String clientIp(final HttpServletRequest request) {
        final String cfHeader = request.getHeader(CF_CONNECTING_IP_HEADER);
        final String cfConnectingIp = cfHeader == null ? null : cfHeader.trim();
        if (cfConnectingIp != null && isIpLiteral(cfConnectingIp)) {
            return cfConnectingIp;
        }
        final String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        final String forwarded = firstForwardedIp(request.getHeader(FORWARDED_FOR_HEADER));
        return forwarded != null ? forwarded : remoteAddr;
    }

    private boolean isTrustedProxy(@Nullable final String ipAddress) {
        return ipAddress != null
            && isIpLiteral(ipAddress)
            && trustedProxies.stream().anyMatch(proxy -> proxy.matches(ipAddress));
    }

    @Nullable
    private static String firstForwardedIp(@Nullable final String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        final int comma = header.indexOf(',');
        final String first = (comma < 0 ? header : header.substring(0, comma)).trim();
        return isIpLiteral(first) ? first : null;
    }

    private static boolean isIpLiteral(final String value) {
        try {
            return InetAddress.ofLiteral(value) != null;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private static List<IpAddressMatcher> parseCidrs(final List<String> raw) {
        return raw.stream()
            .filter(entry -> entry != null && !entry.isBlank())
            .map(ClientIpResolver::parseCidr)
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<IpAddressMatcher> parseCidr(final String entry) {
        try {
            return Optional.of(new IpAddressMatcher(entry.trim()));
        } catch (final IllegalArgumentException ex) {
            LOG.warn("Skipped malformed trusted-proxy CIDR cidr={}", LogSanitizer.forLog(entry));
            return Optional.empty();
        }
    }
}
