package com.betterreads.searchmiss;

import java.util.Locale;

public final class SearchQueryKey {

    private SearchQueryKey() {
    }

    public static String of(final String query) {
        return query.strip().toLowerCase(Locale.ROOT);
    }
}
