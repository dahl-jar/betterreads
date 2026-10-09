package com.betterreads.clients.itunes;

import java.net.URI;
import java.util.Optional;
import java.util.function.Predicate;

public record ItunesBook(String artworkUrl, String storeUrl) {

    private static final String HTTPS = "https";

    private static final String ARTWORK_HOST_SUFFIX = ".mzstatic.com";

    private static final String STORE_HOST = "books.apple.com";

    public static Optional<ItunesBook> of(final String artworkUrl, final String storeUrl) {
        if (!isHttpsHost(artworkUrl, host -> host.endsWith(ARTWORK_HOST_SUFFIX))
            || !isHttpsHost(storeUrl, STORE_HOST::equals)) {
            return Optional.empty();
        }
        return Optional.of(new ItunesBook(artworkUrl, storeUrl));
    }

    private static boolean isHttpsHost(final String url, final Predicate<String> accepts) {
        try {
            final URI uri = URI.create(url);
            final String host = uri.getHost();
            return HTTPS.equals(uri.getScheme()) && host != null && accepts.test(host);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
