package com.betterreads.integration.image;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decides whether a cover URL is safe to fetch server-side.
 *
 * <p>Cover URLs come from external catalog responses, so a compromised source could point one at a
 * cluster service or a cloud metadata endpoint. Only {@code http}/{@code https} URLs whose host
 * resolves entirely to public addresses are allowed; loopback, link-local, site-local, and other
 * non-routable targets are rejected, and a host that fails to resolve is rejected rather than tried.
 */
@Component
public class CoverUrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final int BYTE_MASK = 0xFF;

    private static final int IPV4_BYTES = 4;

    private static final int IPV6_BYTES = 16;

    private static final int CGNAT_FIRST_OCTET = 100;

    private static final int CGNAT_SECOND_OCTET_MASK = 0xC0;

    private static final int CGNAT_SECOND_OCTET_PREFIX = 0x40;

    private static final int ULA_PREFIX_MASK = 0xFE;

    private static final int ULA_PREFIX = 0xFC;

    /** Returns true when the URL is an http(s) link to a host that resolves only to public addresses. */
    public boolean isAllowed(final String url) {
        final URI uri = parse(url);
        if (uri == null) {
            return false;
        }
        final String scheme = uri.getScheme();
        final String host = uri.getHost();
        if (scheme == null || host == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return resolvesToPublicOnly(host);
    }

    private static @Nullable URI parse(final String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static boolean resolvesToPublicOnly(final String host) {
        try {
            final InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (final InetAddress address : addresses) {
                if (isNonPublic(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isNonPublic(final InetAddress address) {
        return address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isAnyLocalAddress()
            || address.isMulticastAddress()
            || isCarrierGradeNat(address)
            || isUniqueLocalIpv6(address);
    }

    /**
     * Returns true for the 100.64.0.0/10 shared-address space, which {@link InetAddress} does not
     * flag as site-local. Overlay and mesh networks route private hosts here, so it must be refused.
     */
    private static boolean isCarrierGradeNat(final InetAddress address) {
        final byte[] octets = address.getAddress();
        return octets.length == IPV4_BYTES
            && (octets[0] & BYTE_MASK) == CGNAT_FIRST_OCTET
            && (octets[1] & CGNAT_SECOND_OCTET_MASK) == CGNAT_SECOND_OCTET_PREFIX;
    }

    /**
     * Returns true for the fc00::/7 unique-local range, which {@link InetAddress#isSiteLocalAddress}
     * covers only for the deprecated fec0::/10 prefix, not the current fc00::/7.
     */
    private static boolean isUniqueLocalIpv6(final InetAddress address) {
        final byte[] octets = address.getAddress();
        return octets.length == IPV6_BYTES && (octets[0] & ULA_PREFIX_MASK) == ULA_PREFIX;
    }
}
