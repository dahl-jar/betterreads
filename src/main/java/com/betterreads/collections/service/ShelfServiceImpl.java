package com.betterreads.collections.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.dto.UpdateEntryRequest;
import com.betterreads.collections.entity.ReadingStatus;
import com.betterreads.collections.entity.ShelfEntry;
import com.betterreads.collections.mapper.ShelfEntryMapper;
import com.betterreads.collections.repository.ShelfEntryRepository;
import com.betterreads.common.exception.InvalidRequestException;
import com.betterreads.common.exception.ResourceNotFoundException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link ShelfService}, backed by {@link ShelfEntryRepository}. */
@Service
public class ShelfServiceImpl implements ShelfService {

    private static final Logger LOG = LoggerFactory.getLogger(ShelfServiceImpl.class);

    private static final int MAX_UPSERT_ATTEMPTS = 3;

    private final ShelfEntryRepository entries;

    private final BookRepository books;

    private final ShelfEntryMapper mapper;

    private final ShelfWriter writer;

    public ShelfServiceImpl(
        final ShelfEntryRepository entries,
        final BookRepository books,
        final ShelfEntryMapper mapper,
        final ShelfWriter writer) {
        this.entries = entries;
        this.books = books;
        this.mapper = mapper;
        this.writer = writer;
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
     * Both are transient: the row the winner wrote is there to read on the next attempt, so the write
     * retries up to {@value #MAX_UPSERT_ATTEMPTS} times. Each attempt needs a fresh transaction
     * because the conflict rolls the previous one back, so the write runs in {@link ShelfWriter}, a
     * separate bean reached through the Spring proxy.
     */
    private ShelfEntryResponse upsert(
        final Long userId, final String bookKey, final Consumer<ShelfEntry> change) {
        final Book book = requireBook(bookKey);
        DataAccessException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_UPSERT_ATTEMPTS; attempt++) {
            try {
                return writer.applyToShelf(userId, book, change);
            } catch (final DataIntegrityViolationException | OptimisticLockingFailureException conflict) {
                lastConflict = conflict;
                LOG.warn("collection.upsert conflict, retrying attempt={} userId={} bookId={}",
                    attempt, userId, book.getBookId());
            }
        }
        throw lastConflict;
    }

    @Override
    @Transactional
    public ShelfEntryResponse updateEntry(
        final Long userId, final String bookKey, final UpdateEntryRequest request) {
        final Book book = requireBook(bookKey);
        final ShelfEntry entry = entries.findByUserIdAndBookId(userId, book.getBookId())
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
        final Book book = requireBook(bookKey);
        entries.deleteByUserIdAndBookId(userId, book.getBookId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShelfEntryResponse> list(final Long userId, final @Nullable ReadingStatus status) {
        final List<ShelfEntry> shelf = status == null
            ? entries.findByUserIdOrderByCreatedAtDesc(userId)
            : entries.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        final Map<Long, Book> booksById = books.findByBookIdIn(
                shelf.stream().map(ShelfEntry::getBookId).toList())
            .stream()
            .collect(Collectors.toMap(Book::getBookId, Function.identity()));
        return shelf.stream()
            .map(entry -> resolveAndMap(entry, booksById))
            .toList();
    }

    private ShelfEntryResponse saveAndMap(final ShelfEntry entry, final Book book) {
        return mapper.toResponse(entries.save(entry), book);
    }

    private ShelfEntryResponse resolveAndMap(final ShelfEntry entry, final Map<Long, Book> booksById) {
        final Book book = booksById.get(entry.getBookId());
        if (book == null) {
            throw new IllegalStateException(
                "shelf row references a missing book bookId=" + entry.getBookId());
        }
        return mapper.toResponse(entry, book);
    }

    private Book requireBook(final String bookKey) {
        return books.findByDedupKey(bookKey)
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
