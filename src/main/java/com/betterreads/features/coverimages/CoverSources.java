package com.betterreads.features.coverimages;

import java.util.Optional;
import java.util.function.Supplier;

import com.betterreads.book.Book;
import com.betterreads.booksource.CoverSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcoverbook.HardcoverClient;
import com.betterreads.clients.itunes.ItunesApi;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.CodecException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
class CoverSources {

    private static final Logger LOG = LoggerFactory.getLogger(CoverSources.class);

    private final ItunesApi itunes;

    private final HardcoverClient hardcover;

    private final OpenLibraryClient openLibrary;

    CoverSources(final ItunesApi itunes, final HardcoverClient hardcover, final OpenLibraryClient openLibrary) {
        this.itunes = itunes;
        this.hardcover = hardcover;
        this.openLibrary = openLibrary;
    }

    Optional<CoverCandidate> find(final CoverSource source, final Book book) {
        return switch (source) {
            case APPLE_BOOKS -> Optional.ofNullable(book.getIsbn())
                .flatMap(itunes::lookupByIsbn)
                .map(apple -> new CoverCandidate(source, apple.artworkUrl(), apple.storeUrl()));
            case HARDCOVER -> sourceCover(source, () -> Optional.ofNullable(book.getHardcoverId())
                .flatMap(hardcover::fetchByHardcoverId));
            case OPEN_LIBRARY -> sourceCover(source, () -> Optional.ofNullable(book.getOpenLibraryWorkKey())
                .flatMap(openLibrary::fetchByWorkKey));
            case GOOGLE_BOOKS -> Optional.empty();
        };
    }

    private static Optional<CoverCandidate> sourceCover(
        final CoverSource source, final Supplier<Optional<SourceBook>> lookup) {
        try {
            return lookup.get()
                .map(SourceBook::coverUrl)
                .filter(url -> !url.isBlank())
                .map(url -> new CoverCandidate(source, url, null));
        } catch (WebClientException | CodecException ex) {
            LOG.debug("catalog.cover-upgrade skipped source={} ({})",
                LogSanitizer.forLog(source.name()), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
