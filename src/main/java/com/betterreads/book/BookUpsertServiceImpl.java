package com.betterreads.book;

import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;

import com.betterreads.logging.LogSanitizer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matches an existing book on its source id columns and announces each catalog write. */
@Service
class BookUpsertServiceImpl implements BookUpsertService {

    private static final Logger LOG = LoggerFactory.getLogger(BookUpsertServiceImpl.class);

    private static final String NO_BOOK = "no book with id ";

    private final BookRepository bookRepository;

    private final AuthorResolver authorResolver;

    private final SeriesChangeRecorder seriesChanges;

    private final MetadataChangeRecorder metadataChanges;

    private final EntityManager entityManager;

    private final ApplicationEventPublisher events;

    private final List<SourceIdentityLookup> identityLookups;

    // PMD.ExcessiveParameterList: six injected collaborators, the constructor is Spring's injection point.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    BookUpsertServiceImpl(
        final BookRepository bookRepository,
        final AuthorResolver authorResolver,
        final SeriesChangeRecorder seriesChanges,
        final MetadataChangeRecorder metadataChanges,
        final EntityManager entityManager,
        final ApplicationEventPublisher events
    ) {
        this.bookRepository = bookRepository;
        this.authorResolver = authorResolver;
        this.seriesChanges = seriesChanges;
        this.metadataChanges = metadataChanges;
        this.entityManager = entityManager;
        this.events = events;
        this.identityLookups = List.of(
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

    @Override
    @Transactional
    public Book upsertFromSource(final SourceBook source) {
        return upsert(source, stored -> source.hardcoverId() != null);
    }

    @Override
    @Transactional
    public Book upsertFromSource(final MergedBook merged) {
        return upsert(merged.book(), merged::hasSeriesAuthority);
    }

    private Book upsert(final SourceBook source, final Predicate<@Nullable String> seriesAuthority) {
        final Book book = findExistingForSource(source)
            .map(this::lockAndRefresh)
            .orElseGet(Book::new);
        final boolean authority = seriesAuthority.test(book.getHardcoverId());
        book.applyFrom(source);
        final List<SeriesEntry> seriesBefore = book.getSeries();
        book.applySeries(source.series(), authority);
        seriesChanges.recordAndRequestCheck(book, seriesBefore);
        if (!book.isVerified(VerifiedField.AUTHORS)) {
            replaceCredits(book, source.authors());
        }
        return saveAndAnnounce(book);
    }

    @Override
    @Transactional
    public Book applyCredits(final long bookId, final List<SourceAuthor> credits) {
        final Book book = locked(bookId);
        if (book.isVerified(VerifiedField.AUTHORS)) {
            stampIfChanged(book, book.replaceCredits(Credits.reordered(book.getCredits(), credits)));
        } else {
            replaceCredits(book, credits);
        }
        return saveAndAnnounce(book);
    }

    @Override
    @Transactional
    public Book applyVerified(final long bookId, final VerifiedMetadata metadata, final int checkVersion) {
        final Book book = locked(bookId);
        final Map<VerifiedField, @Nullable String> before = BookSnapshot.of(book);
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
        metadataChanges.record(bookId, before, book, metadata.evidence(), checkVersion);
        LOG.info("catalog.metadata-check bookId={} verified={}", bookId,
            LogSanitizer.forLog(book.getVerifiedFields().toString()));
        return saveAndAnnounce(book);
    }

    @Override
    @Transactional
    public boolean deferMetadataCheck(final long bookId, final OffsetDateTime retryAt, final int maxAttempts) {
        final Book book = locked(bookId);
        final boolean retrying = book.deferMetadataCheck(retryAt, maxAttempts, OffsetDateTime.now(ZoneOffset.UTC));
        bookRepository.save(book);
        return retrying;
    }

    private Book saveAndAnnounce(final Book book) {
        final Book saved = bookRepository.save(book);
        events.publishEvent(new BookChangedEvent(saved.getBookId()));
        return saved;
    }

    private Book locked(final long bookId) {
        return bookRepository.findForUpdate(bookId).orElseThrow(() -> new IllegalArgumentException(NO_BOOK + bookId));
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
        return identityLookups.stream()
            .map(lookup -> lookup.find(source))
            .flatMap(Optional::stream)
            .findFirst();
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
