package com.betterreads.clients.http;

import java.net.InetAddress;

public final class UrlGuards {

    private UrlGuards() {
    }

    public static PublicUrlGuard allowing(final boolean allowed) {
        return new PublicUrlGuard() {
            @Override
            public boolean isAllowed(final String url) {
                return allowed;
            }

            @Override
            public boolean arePublic(final InetAddress... addresses) {
                return allowed;
            }
        };
    }

    public static PublicUrlGuard checkingOnlyAtConnect() {
        return new PublicUrlGuard() {
            @Override
            public boolean isAllowed(final String url) {
                return true;
            }
        };
    }
}
