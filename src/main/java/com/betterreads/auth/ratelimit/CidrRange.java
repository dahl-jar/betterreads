package com.betterreads.auth.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

/**
 * IPv4/IPv6 CIDR range. Package-private helper for trusted-proxy matching in
 * {@link RateLimitFilter}.
 */
final class CidrRange {

    private static final int BITS_PER_BYTE = 8;

    private final byte[] networkBytes;

    private final int prefixLength;

    private CidrRange(final byte[] networkBytes, final int prefixLength) {
        this.networkBytes = networkBytes.clone();
        this.prefixLength = prefixLength;
    }

    /**
     * Parses CIDR strings like {@code 10.0.0.0/8} or {@code 2001:db8::/32}. Returns null on
     * malformed input.
     */
    @Nullable
    static CidrRange parse(@Nullable final String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return null;
        }
        final String trimmed = cidr.trim();
        final int slash = trimmed.indexOf('/');
        if (slash < 0) {
            return null;
        }
        final String addressPart = trimmed.substring(0, slash);
        final String prefixPart = trimmed.substring(slash + 1);
        try {
            final InetAddress address = InetAddress.getByName(addressPart);
            final int prefix = Integer.parseInt(prefixPart);
            final byte[] addressBytes = address.getAddress();
            if (prefix < 0 || prefix > addressBytes.length * BITS_PER_BYTE) {
                return null;
            }
            return new CidrRange(maskToPrefix(addressBytes, prefix), prefix);
        } catch (final UnknownHostException | NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Returns true when {@code address} is in this range. False on family mismatch.
     */
    boolean contains(final InetAddress address) {
        final byte[] candidateBytes = address.getAddress();
        return candidateBytes.length == networkBytes.length
            && Arrays.equals(maskToPrefix(candidateBytes, prefixLength), networkBytes);
    }

    @SuppressWarnings("PMD.AvoidArrayLoops")
    private static byte[] maskToPrefix(final byte[] addressBytes, final int prefix) {
        final byte[] masked = new byte[addressBytes.length];
        for (int i = 0; i < addressBytes.length; i++) {
            final int bitsRemaining = prefix - i * BITS_PER_BYTE;
            if (bitsRemaining >= BITS_PER_BYTE) {
                masked[i] = addressBytes[i];
            } else if (bitsRemaining <= 0) {
                masked[i] = 0;
            } else {
                final int byteMask = 0xFF << (BITS_PER_BYTE - bitsRemaining) & 0xFF;
                masked[i] = (byte) (addressBytes[i] & byteMask);
            }
        }
        return masked;
    }
}
