package com.betterreads.catalog.image;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives a short, stable version token from a cover URL.
 *
 * <p>The object key and the served URL both carry this token, so a changed cover URL produces a new
 * storage key and a new browser URL together. The browser then fetches the new cover instead of a
 * cached one, and the storage read misses instead of returning the object stored for the old URL.
 */
public final class CoverVersion {

    private static final int VERSION_BYTES = 16;

    private CoverVersion() {
    }

    /** Returns the hex version token for the cover URL. */
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
