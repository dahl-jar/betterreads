package com.betterreads.auth;

import java.util.Locale;

/** Email normalization shared across the auth services. */
public final class Emails {

    private Emails() {
    }

    /** Returns the email trimmed and lowercased, the form stored and looked up by. */
    public static String normalize(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
