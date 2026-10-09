package com.betterreads.features.coverimages;

import java.util.List;
import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.booksource.CoverSource;
import com.betterreads.images.CoverFetcher;
import com.betterreads.images.Image;
import com.betterreads.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
class CoverPicker {

    private static final Logger LOG = LoggerFactory.getLogger(CoverPicker.class);

    private static final List<CoverSource> ORDER =
        List.of(CoverSource.APPLE_BOOKS, CoverSource.HARDCOVER, CoverSource.OPEN_LIBRARY);

    private final CoverSources sources;

    private final CoverBlockRepository blocks;

    private final CoverFetcher fetcher;

    private final CoverCheck check;

    CoverPicker(
        final CoverSources sources,
        final CoverBlockRepository blocks,
        final CoverFetcher fetcher,
        final CoverCheck check
    ) {
        this.sources = sources;
        this.blocks = blocks;
        this.fetcher = fetcher;
        this.check = check;
    }

    Optional<PickedCover> best(final Book book) {
        return ORDER.stream()
            .flatMap(source -> sources.find(source, book).stream())
            .filter(candidate -> !blocks.isBlocked(candidate.url()))
            .flatMap(candidate -> fetch(candidate.url())
                .filter(image -> check.passes(image.bytes()))
                .map(image -> new PickedCover(candidate, image))
                .stream())
            .findFirst();
    }

    boolean rejectsCurrent(final Book book) {
        return Optional.ofNullable(book.getCoverUrl())
            .map(url -> blocks.isBlocked(url)
                || fetch(url).map(image -> check.isWrongImage(image.bytes())).orElse(false))
            .orElse(false);
    }

    private Optional<Image> fetch(final String url) {
        try {
            return fetcher.fetch(url);
        } catch (WebClientException ex) {
            LOG.debug("catalog.cover-upgrade could not fetch {} ({})",
                LogSanitizer.forLog(url), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
