package com.betterreads.features.shelves;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.betterreads.bookaccess.BookIdLookup;
import com.betterreads.bookaccess.BookSummary;
import com.betterreads.bookaccess.BookSummaryReader;
import com.betterreads.db.ConflictRetry;
import com.betterreads.errors.InvalidRequestException;
import com.betterreads.errors.ResourceNotFoundException;
import com.betterreads.ratings.ReaderRatings;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ShelfServiceImpl implements ShelfService {

    private static final Logger LOG = LoggerFactory.getLogger(ShelfServiceImpl.class);

    private static final int MAX_UPSERT_ATTEMPTS = 3;

    private final ShelfEntryRepository entries;

    private final BookSummaryReader bookSummaries;

    private final BookIdLookup bookIds;

    private final ShelfEntryMapper mapper;

    private final ShelfWriter writer;

    private final ReaderRatings ratings;

    // PMD.ExcessiveParameterList: six injected collaborators, the constructor is Spring's injection point.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    ShelfServiceImpl(
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
     * Two first writes for the same user and book race on the unique key, and two edits race on the
     * row version, so the write retries in a fresh transaction on either conflict.
     */
    private ShelfEntryResponse upsert(
        final Long userId, final String bookKey, final Consumer<ShelfEntry> change) {
        final BookSummary book = requireBook(bookKey);
        final Integer myRating = ratings.ratingOf(userId, book.bookId()).orElse(null);
        return ConflictRetry.retryOnConflict(MAX_UPSERT_ATTEMPTS, LOG,
            "Shelf upsert conflict, retrying userId=" + userId + " bookId=" + book.bookId(),
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
        return mapper.toResponse(
            entries.save(entry), book, ratings.ratingOf(userId, book.bookId()).orElse(null));
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
        final Map<Long, BookSummary> booksById = bookSummaries.summariesKeyedById(shelfBookIds);
        final Map<Long, Integer> ratingsByBookId = ratings.ratingsOf(userId, shelfBookIds);
        return shelf.stream()
            .map(entry -> toResponse(entry, booksById, ratingsByBookId))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShelfCountsResponse countsForBook(final String bookKey) {
        final StatusCounts counts =
            StatusCounts.from(entries.countByStatusForBook(bookIds.requireBookId(bookKey)));
        return new ShelfCountsResponse(
            counts.of(ReadingStatus.WANT_TO_READ),
            counts.of(ReadingStatus.CURRENTLY_READING),
            counts.of(ReadingStatus.FINISHED),
            counts.of(ReadingStatus.DROPPED));
    }

    @Override
    @Transactional(readOnly = true)
    public MyShelfCountsResponse countsForUser(final Long userId) {
        return new MyShelfCountsResponse(entries.countByUserId(userId));
    }

    private ShelfEntryResponse toResponse(
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
        if (started != null && finished != null && finished.isBefore(started)) {
            throw new InvalidRequestException("Finished date cannot be before the started date");
        }
        entry.setStartedAt(started);
        entry.setFinishedAt(finished);
    }

    private static @Nullable LocalDate firstNonNull(
        final @Nullable LocalDate preferred, final @Nullable LocalDate fallback) {
        return preferred == null ? fallback : preferred;
    }
}
