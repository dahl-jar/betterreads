package com.betterreads.clients.http;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

public final class Redirects {

    public static final int MAX_HOPS = 3;

    private Redirects() {
    }

    public static <T> Optional<T> follow(
        final String url, final Predicate<String> allowed, final Function<String, Hop<T>> request) {
        String target = url;
        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            if (!allowed.test(target)) {
                return Optional.empty();
            }
            final Hop<T> result = request.apply(target);
            final String location = result.location();
            if (location == null) {
                return result.result();
            }
            try {
                target = URI.create(target).resolve(location).toString();
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public record Hop<T>(Optional<T> result, @Nullable String location) {

        public static <T> Hop<T> done(final Optional<T> result) {
            return new Hop<>(result, null);
        }

        public static <T> Hop<T> to(final @Nullable String location) {
            return location == null ? done(Optional.empty()) : new Hop<>(Optional.empty(), location);
        }

        public static <T> Hop<T> of(
            final @Nullable ResponseEntity<byte[]> response, final Function<ResponseEntity<byte[]>, Optional<T>> body) {
            if (response == null) {
                return done(Optional.empty());
            }
            if (response.getStatusCode().is3xxRedirection()) {
                return to(response.getHeaders().getFirst(HttpHeaders.LOCATION));
            }
            final boolean ok = response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
            return done(ok ? body.apply(response) : Optional.empty());
        }
    }
}
