package com.betterreads.clients.websearch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class SourceHosts {

    private SourceHosts() {
    }

    static Optional<String> hostOf(final String url) {
        try {
            return Optional.ofNullable(new URI(url).getHost()).map(host -> host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    static boolean isListed(final String url, final List<String> domains) {
        return hostOf(url)
            .filter(host -> domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain)))
            .isPresent();
    }
}
