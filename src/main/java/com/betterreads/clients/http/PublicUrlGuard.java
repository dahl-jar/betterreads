package com.betterreads.clients.http;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decides whether a URL from an external response is safe to fetch server-side.
 *
 * <p>URLs from external responses can point at an internal service or a cloud metadata endpoint, so only
 * http(s) URLs whose host resolves entirely to public addresses pass. A host that fails to resolve is rejected.
 */
@Component
public class PublicUrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final int BYTE_MASK = 0xFF;

    private static final int IPV4_BYTES = 4;

    private static final int IPV6_BYTES = 16;

    private static final int CGNAT_FIRST_OCTET = 100;

    private static final int CGNAT_SECOND_OCTET_MASK = 0xC0;

    private static final int CGNAT_SECOND_OCTET_PREFIX = 0x40;

    private static final int ULA_PREFIX_MASK = 0xFE;

    private static final int ULA_PREFIX = 0xFC;

    private final HostResolver resolver;

    public PublicUrlGuard() {
        this(InetAddress::getAllByName);
    }

    public PublicUrlGuard(final HostResolver resolver) {
        this.resolver = resolver;
    }

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

    private boolean resolvesToPublicOnly(final String host) {
        try {
            return arePublic(addressesOf(host));
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    public InetAddress[] addressesOf(final String host) throws UnknownHostException {
        return resolver.resolve(host);
    }

    public boolean arePublic(final InetAddress... addresses) {
        return addresses.length > 0 && Arrays.stream(addresses).noneMatch(PublicUrlGuard::isNonPublic);
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

    /** Overlay and mesh networks put private hosts in 100.64.0.0/10, which InetAddress does not flag as site-local. */
    private static boolean isCarrierGradeNat(final InetAddress address) {
        final byte[] octets = address.getAddress();
        return octets.length == IPV4_BYTES
            && (octets[0] & BYTE_MASK) == CGNAT_FIRST_OCTET
            && (octets[1] & CGNAT_SECOND_OCTET_MASK) == CGNAT_SECOND_OCTET_PREFIX;
    }

    /** isSiteLocalAddress only flags the deprecated fec0::/10 prefix, so fc00::/7 needs its own check. */
    private static boolean isUniqueLocalIpv6(final InetAddress address) {
        final byte[] octets = address.getAddress();
        return octets.length == IPV6_BYTES && (octets[0] & ULA_PREFIX_MASK) == ULA_PREFIX;
    }

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
