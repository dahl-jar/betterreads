package com.betterreads.features.shelves;

import com.betterreads.bookaccess.BookSummary;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Builds the shelf response from a shelf row and its book. */
@Component
class ShelfEntryMapper {

    public ShelfEntryResponse toResponse(
        final ShelfEntry entry, final BookSummary book, final @Nullable Integer myRating) {
        return new ShelfEntryResponse(
            book.dedupKey(),
            book.title(),
            book.authors(),
            book.servedCoverUrl(),
            entry.getStatus(),
            entry.isFavorite(),
            entry.getStartedAt(),
            entry.getFinishedAt(),
            entry.getNotes(),
            entry.getCreatedAt().toLocalDate(),
            book.averageRating(),
            myRating);
    }
}
