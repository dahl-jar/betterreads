package com.betterreads.collections.mapper;

import com.betterreads.catalog.dto.BookSummary;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.entity.ShelfEntry;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Builds a {@link ShelfEntryResponse} from a shelf row and its book summary. */
@Component
public class ShelfEntryMapper {

    /**
     * Combines the shelf state from {@code entry} with the book fields from {@code book}.
     *
     * @param myRating the reader's own rating for the book, null when they have not rated it
     */
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
