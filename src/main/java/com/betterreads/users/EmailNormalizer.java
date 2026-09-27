package com.betterreads.users;

import java.util.Locale;

/** Emails are stored trimmed and lowercased, so lookups normalize the same way. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
