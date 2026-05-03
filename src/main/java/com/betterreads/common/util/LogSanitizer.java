package com.betterreads.common.util;

import org.jspecify.annotations.Nullable;

/**
 * Strips CR/LF from values before they reach log messages. Defends against log-forgery via
 * user-controlled input (CWE-117).
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /**
     * Returns the input with carriage returns and line feeds removed. Null in, null out.
     */
    @Nullable
    public static String forLog(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\r", "").replace("\n", "");
    }
}
