package com.betterreads.features.shelves;

import java.util.function.Consumer;

import com.betterreads.bookaccess.BookSummary;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Shelf upsert in its own bean so each retry attempt gets a fresh transaction. */
@Component
class ShelfWriter {

    private final ShelfEntryRepository entries;

    private final ShelfEntryMapper mapper;

    ShelfWriter(final ShelfEntryRepository entries, final ShelfEntryMapper mapper) {
        this.entries = entries;
        this.mapper = mapper;
    }

    /** saveAndFlush raises a duplicate-key conflict inside this call, so the caller's retry sees it. */
    @Transactional
    public ShelfEntryResponse applyToShelf(
        final Long userId, final BookSummary book, final Consumer<ShelfEntry> change,
        final @Nullable Integer myRating) {
        final ShelfEntry entry = entries.findByUserIdAndBookId(userId, book.bookId())
            .orElseGet(() -> new ShelfEntry(userId, book.bookId()));
        change.accept(entry);
        return mapper.toResponse(entries.saveAndFlush(entry), book, myRating);
    }
}
