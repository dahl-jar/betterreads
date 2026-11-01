package com.betterreads.logging;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

public final class LogSanitizer {

    private static final Pattern OTHER_CONTROL_CHARACTERS =
        Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029&&[^\\r\\n]]");

    private LogSanitizer() {
    }

    @Nullable
    public static String forLog(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        return OTHER_CONTROL_CHARACTERS.matcher(value).replaceAll("").replace("\r", "").replace("\n", "");
    }
}
