package com.betterreads.collections.service;

import java.util.function.Consumer;

import com.betterreads.catalog.entity.Book;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.entity.ShelfEntry;
import com.betterreads.collections.mapper.ShelfEntryMapper;
import com.betterreads.collections.repository.ShelfEntryRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional write for the shelf upsert, in its own bean so each attempt runs in a fresh
 * transaction reached through the Spring proxy. {@link ShelfServiceImpl#changeStatus} retries on a
 * concurrent-insert conflict, and the retry needs a transaction the rolled-back first attempt did
 * not poison.
 */
@Component
public class ShelfWriter {

    private final ShelfEntryRepository entries;

    private final ShelfEntryMapper mapper;

    public ShelfWriter(final ShelfEntryRepository entries, final ShelfEntryMapper mapper) {
        this.entries = entries;
        this.mapper = mapper;
    }

    /**
     * Loads the user's entry for the book or opens a new one, applies {@code change}, and flushes.
     *
     * <p>{@code saveAndFlush} so a duplicate-key conflict from a concurrent first touch is thrown
     * here, inside this transaction, where the caller can catch it and retry, rather than at commit.
     */
    @Transactional
    public ShelfEntryResponse applyToShelf(
        final Long userId, final Book book, final Consumer<ShelfEntry> change) {
        final ShelfEntry entry = entries.findByUserIdAndBookId(userId, book.getBookId())
            .orElseGet(() -> new ShelfEntry(userId, book.getBookId()));
        change.accept(entry);
        return mapper.toResponse(entries.saveAndFlush(entry), book);
    }
}
