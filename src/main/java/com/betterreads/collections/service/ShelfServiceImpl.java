package com.betterreads.collections.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.catalog.dto.BookSummary;
import com.betterreads.catalog.service.read.BookIdLookup;
import com.betterreads.catalog.service.read.BookSummaryReader;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.dto.UpdateEntryRequest;
import com.betterreads.collections.entity.ReadingStatus;
import com.betterreads.collections.entity.ShelfEntry;
import com.betterreads.collections.mapper.ShelfEntryMapper;
import com.betterreads.collections.repository.ShelfEntryRepository;
import com.betterreads.common.exception.InvalidRequestException;
import com.betterreads.common.exception.ResourceNotFoundException;
import com.betterreads.common.util.ConflictRetry;
import com.betterreads.reviews.service.ReaderRatings;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link ShelfService}, backed by {@link ShelfEntryRepository}. */
@Service
public class ShelfServiceImpl implements ShelfService {

    private static final Logger LOG = LoggerFactory.getLogger(ShelfServiceImpl.class);

    private static final int MAX_UPSERT_ATTEMPTS = 3;

    private final ShelfEntryRepository entries;

    private final BookSummaryReader bookSummaries;

    private final BookIdLookup bookIds;

    private final ShelfEntryMapper mapper;

    private final ShelfWriter writer;

    private final ReaderRatings ratings;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ShelfServiceImpl(
        final ShelfEntryRepository entries,
        final BookSummaryReader bookSummaries,
        final BookIdLookup bookIds,
        final ShelfEntryMapper mapper,
        final ShelfWriter writer,
        final ReaderRatings ratings) {
        this.entries = entries;
        this.bookSummaries = bookSummaries;
        this.bookIds = bookIds;
        this.mapper = mapper;
        this.writer = writer;
        this.ratings = ratings;
    }

    @Override
    public ShelfEntryResponse changeStatus(
        final Long userId, final String bookKey, final ReadingStatus status) {
        return upsert(userId, bookKey, entry -> entry.moveTo(status));
    }

    @Override
    public ShelfEntryResponse markFavorite(final Long userId, final String bookKey, final boolean favorite) {
        return upsert(userId, bookKey, entry -> entry.setFavorite(favorite));
    }

    /**
     * Applies {@code change} to the user's entry for the book, creating the row on first touch.
     *
     * <p>Concurrent writes to the same user and book conflict two ways: two first touches both insert
     * and the loser hits the {@code (user_id, book_id)} unique constraint, and two writes to an
     * existing row both bump the {@code @Version} and the loser hits an optimistic-lock failure.
     * {@link ConflictRetry} re-runs the write in {@link ShelfWriter}'s fresh transaction for both.
     */
    private ShelfEntryResponse upsert(
        final Long userId, final String bookKey, final Consumer<ShelfEntry> change) {
        final BookSummary book = requireBook(bookKey);
        final Integer myRating = ratings.ratingOf(userId, book.bookId()).orElse(null);
        return ConflictRetry.retryOnConflict(MAX_UPSERT_ATTEMPTS, LOG,
            "collection.upsert conflict, retrying userId=" + userId + " bookId=" + book.bookId(),
            () -> writer.applyToShelf(userId, book, change, myRating));
    }

    @Override
    @Transactional
    public ShelfEntryResponse updateEntry(
        final Long userId, final String bookKey, final UpdateEntryRequest request) {
        final BookSummary book = requireBook(bookKey);
        final ShelfEntry entry = entries.findByUserIdAndBookId(userId, book.bookId())
            .orElseThrow(() -> new ResourceNotFoundException("Book is not on the shelf: " + bookKey));
        applyDates(entry, request);
        if (request.notes() != null) {
            entry.setNotes(request.notes());
        }
        return saveAndMap(entry, book);
    }

    @Override
    @Transactional
    public void remove(final Long userId, final String bookKey) {
        entries.deleteByUserIdAndBookId(userId, bookIds.requireBookId(bookKey));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShelfEntryResponse> list(final Long userId, final @Nullable ReadingStatus status) {
        final List<ShelfEntry> shelf = status == null
            ? entries.findByUserIdOrderByCreatedAtDesc(userId)
            : entries.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        final List<Long> shelfBookIds = shelf.stream().map(ShelfEntry::getBookId).toList();
        final Map<Long, BookSummary> booksById = bookSummaries.summariesByIds(shelfBookIds).stream()
            .collect(Collectors.toMap(BookSummary::bookId, Function.identity()));
        final Map<Long, Integer> ratingsByBookId = ratings.ratingsOf(userId, shelfBookIds);
        return shelf.stream()
            .map(entry -> resolveAndMap(entry, booksById, ratingsByBookId))
            .toList();
    }

    private ShelfEntryResponse saveAndMap(final ShelfEntry entry, final BookSummary book) {
        return mapper.toResponse(
            entries.save(entry), book,
            ratings.ratingOf(entry.getUserId(), book.bookId()).orElse(null));
    }

    private ShelfEntryResponse resolveAndMap(
        final ShelfEntry entry, final Map<Long, BookSummary> booksById,
        final Map<Long, Integer> ratingsByBookId) {
        final BookSummary book = booksById.get(entry.getBookId());
        if (book == null) {
            throw new IllegalStateException(
                "shelf row references a missing book bookId=" + entry.getBookId());
        }
        return mapper.toResponse(entry, book, ratingsByBookId.get(entry.getBookId()));
    }

    private BookSummary requireBook(final String bookKey) {
        return bookSummaries.summaryByKey(bookKey)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + bookKey));
    }

    private static void applyDates(final ShelfEntry entry, final UpdateEntryRequest request) {
        final LocalDate started = firstNonNull(request.startedAt(), entry.getStartedAt());
        final LocalDate finished = firstNonNull(request.finishedAt(), entry.getFinishedAt());
        rejectFinishedBeforeStarted(started, finished);
        if (request.startedAt() != null) {
            entry.setStartedAt(request.startedAt());
        }
        if (request.finishedAt() != null) {
            entry.setFinishedAt(request.finishedAt());
        }
    }

    private static void rejectFinishedBeforeStarted(
        final @Nullable LocalDate started, final @Nullable LocalDate finished) {
        if (started != null && finished != null && finished.isBefore(started)) {
            throw new InvalidRequestException("Finished date cannot be before the started date");
        }
    }

    private static @Nullable LocalDate firstNonNull(
        final @Nullable LocalDate preferred, final @Nullable LocalDate fallback) {
        return preferred == null ? fallback : preferred;
    }
}
