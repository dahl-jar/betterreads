package com.betterreads.collections.service;

import java.util.function.Consumer;

import com.betterreads.catalog.entity.Book;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.entity.ShelfEntry;
import com.betterreads.collections.mapper.ShelfEntryMapper;
import com.betterreads.collections.repository.ShelfEntryRepository;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shelf upsert write, in its own bean so the retry gets a fresh transaction per attempt.
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
     * Applies {@code change} to the user's entry, creating the row on first touch.
     *
     * <p>{@code saveAndFlush} surfaces a duplicate-key conflict inside the transaction, where the
     * caller can retry it, before commit hides it.
     */
    @Transactional
    public ShelfEntryResponse applyToShelf(
        final Long userId, final Book book, final Consumer<ShelfEntry> change,
        final @Nullable Integer myRating) {
        final ShelfEntry entry = entries.findByUserIdAndBookId(userId, book.getBookId())
            .orElseGet(() -> new ShelfEntry(userId, book.getBookId()));
        change.accept(entry);
        return mapper.toResponse(entries.saveAndFlush(entry), book, myRating);
    }
}
