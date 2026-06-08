package com.betterreads.collections.mapper;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.entity.ShelfEntry;

import org.springframework.stereotype.Component;

/** Builds a {@link ShelfEntryResponse} from a shelf row and its book. */
@Component
public class ShelfEntryMapper {

    /**
     * Combines the shelf state from {@code entry} with the book summary from {@code book}.
     *
     * <p>{@code myRating} is always null: there is no per-user rating store yet.
     */
    public ShelfEntryResponse toResponse(final ShelfEntry entry, final Book book) {
        return new ShelfEntryResponse(
            book.getDedupKey(),
            book.getTitle(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            book.getCoverUrl(),
            entry.getStatus(),
            entry.isFavorite(),
            entry.getStartedAt(),
            entry.getFinishedAt(),
            entry.getNotes(),
            entry.getCreatedAt().toLocalDate(),
            book.getAverageRating(),
            null);
    }
}
