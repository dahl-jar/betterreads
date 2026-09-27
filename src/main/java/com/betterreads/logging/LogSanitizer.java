package com.betterreads.logging;

import org.jspecify.annotations.Nullable;

/**
 * User input with CR/LF can forge log lines (CWE-117), so values are stripped of them before logging.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    @Nullable
    public static String forLog(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\r", "").replace("\n", "");
    }
}
