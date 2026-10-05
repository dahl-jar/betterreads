package com.betterreads.book;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;

import com.betterreads.logging.LogSanitizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matches an existing book on its source id columns and evicts its cached detail on write. */
@Service
class BookUpsertServiceImpl implements BookUpsertService {

    private static final Logger LOG = LoggerFactory.getLogger(BookUpsertServiceImpl.class);

    private static final String NO_BOOK = "no book with id ";

    private final BookRepository bookRepository;

    private final AuthorResolver authorResolver;

    private final SeriesChangeRecorder seriesChanges;

    private final EntityManager entityManager;

    BookUpsertServiceImpl(
        final BookRepository bookRepository,
        final AuthorResolver authorResolver,
        final SeriesChangeRecorder seriesChanges,
        final EntityManager entityManager
    ) {
        this.bookRepository = bookRepository;
        this.authorResolver = authorResolver;
        this.seriesChanges = seriesChanges;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = BookDetailCache.NAME, key = "#result.dedupKey")
    public Book upsertFromSource(final SourceBook source) {
        return upsert(source, true);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = BookDetailCache.NAME, key = "#result.dedupKey")
    public Book upsertFromSource(final MergedBook merged) {
        return upsert(merged.book(), merged.resolved(BookFieldSource.HARDCOVER));
    }

    private Book upsert(final SourceBook source, final boolean seriesAuthorityResolved) {
        final Book book = findExistingForSource(source)
            .map(this::lockAndRefresh)
            .orElseGet(Book::new);
        book.applyFrom(source);
        final List<SeriesEntry> seriesBefore = book.getSeries();
        book.applySeries(source.series(), seriesAuthorityResolved);
        seriesChanges.recordAndRequestCheck(book, seriesBefore);
        if (!book.isVerified(VerifiedField.AUTHORS)) {
            replaceCredits(book, source.authors());
        }
        return bookRepository.save(book);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = BookDetailCache.NAME, key = "#result.dedupKey")
    public Book applyCredits(final long bookId, final List<SourceAuthor> credits) {
        final Book book = bookRepository.findForUpdate(bookId)
            .orElseThrow(() -> new IllegalArgumentException(NO_BOOK + bookId));
        if (book.isVerified(VerifiedField.AUTHORS)) {
            stampIfChanged(book, book.replaceCredits(Credits.reordered(book.getCredits(), credits)));
        } else {
            replaceCredits(book, credits);
        }
        return bookRepository.save(book);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = BookDetailCache.NAME, key = "#result.dedupKey")
    public Book applyVerified(final long bookId, final VerifiedMetadata metadata) {
        final Book book = bookRepository.findForUpdate(bookId)
            .orElseThrow(() -> new IllegalArgumentException(NO_BOOK + bookId));
        LOG.info("catalog.metadata-check bookId={} before title={} isbn={} series={} #{}", bookId,
            LogSanitizer.forLog(book.getTitle()), LogSanitizer.forLog(book.getIsbn()),
            LogSanitizer.forLog(book.getSeriesName()), book.getSeriesPosition());
        final List<ResolvedCredit> nonPrimary = book.getCredits().stream()
            .filter(credit -> !credit.getRole().isPrimary())
            .map(credit -> new ResolvedCredit(credit.getAuthor(), credit.getRole()))
            .toList();
        book.applyVerified(metadata, OffsetDateTime.now(ZoneOffset.UTC));
        final List<String> verifiedAuthors = metadata.authors();
        if (verifiedAuthors != null) {
            final List<ResolvedCredit> verified =
                authorResolver.resolve(verifiedAuthors.stream().map(SourceAuthor::ofName).toList());
            if (!verified.isEmpty()) {
                final List<ResolvedCredit> credits = Stream.concat(verified.stream(), nonPrimary.stream()).toList();
                stampIfChanged(book, book.replaceCredits(credits));
            }
        }
        LOG.info("catalog.metadata-check bookId={} verified={} after title={} isbn={} series={} #{}", bookId,
            LogSanitizer.forLog(book.getVerifiedFields().toString()), LogSanitizer.forLog(book.getTitle()),
            LogSanitizer.forLog(book.getIsbn()),
            LogSanitizer.forLog(book.getSeriesName()), book.getSeriesPosition());
        return bookRepository.save(book);
    }

    /**
     * A review can commit a new community rating between the lookup and the save, so the row is
     * locked and re-read before the write.
     */
    private Book lockAndRefresh(final Book existing) {
        final Book locked = bookRepository.findForUpdate(existing.getBookId()).orElse(existing);
        entityManager.refresh(locked);
        return locked;
    }

    private Optional<Book> findExistingForSource(final SourceBook source) {
        return identityLookups().stream()
            .map(lookup -> lookup.find(source))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private List<SourceIdentityLookup> identityLookups() {
        return List.of(
            new SourceIdentityLookup(
                SourceBook::googleBooksVolumeId, bookRepository::findByGoogleBooksVolumeId),
            new SourceIdentityLookup(
                SourceBook::openLibraryWorkKey, bookRepository::findByOpenLibraryWorkKey),
            new SourceIdentityLookup(
                SourceBook::hardcoverId, bookRepository::findByHardcoverId),
            new SourceIdentityLookup(
                SourceBook::locLccn, bookRepository::findByLocLccn),
            new SourceIdentityLookup(
                SourceBook::wikidataQid, bookRepository::findByWikidataQid));
    }

    private record SourceIdentityLookup(
        Function<SourceBook, @Nullable String> idOf,
        Function<String, Optional<Book>> findById
    ) {

        Optional<Book> find(final SourceBook source) {
            final String sourceId = idOf.apply(source);
            return sourceId == null ? Optional.empty() : findById.apply(sourceId);
        }
    }

    /**
     * A null list means the sources did not carry authors, and a list with no usable name is a
     * degenerate response, so both keep the stored authors.
     */
    private void replaceCredits(final Book book, final @Nullable List<SourceAuthor> authors) {
        if (authors == null) {
            return;
        }
        final List<ResolvedCredit> resolved = authorResolver.resolve(authors);
        if (!resolved.isEmpty()) {
            stampIfChanged(book, book.replaceCredits(resolved));
        }
    }

    private static void stampIfChanged(final Book book, final boolean changed) {
        if (changed) {
            book.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
    }
}
