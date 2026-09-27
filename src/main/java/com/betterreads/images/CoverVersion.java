package com.betterreads.images;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Short, stable version token for a cover URL.
 *
 * <p>The object key and the served URL both carry the token, so a changed cover URL misses both the
 * browser cache and the object stored for the old URL.
 */
public final class CoverVersion {

    private static final int VERSION_BYTES = 16;

    private CoverVersion() {
    }

    public static String of(final String coverUrl) {
        final byte[] digest = sha256().digest(coverUrl.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest, 0, VERSION_BYTES);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for cover versioning", ex);
        }
    }
}
