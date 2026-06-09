package com.betterreads.collections.mapper;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.image.CoverImages;
import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.entity.ShelfEntry;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Builds a {@link ShelfEntryResponse} from a shelf row and its book. */
@Component
public class ShelfEntryMapper {

    private final CoverImages coverImages;

    public ShelfEntryMapper(final CoverImages coverImages) {
        this.coverImages = coverImages;
    }

    /**
     * Combines the shelf state from {@code entry} with the book summary from {@code book}.
     *
     * @param myRating the reader's own rating for the book, null when they have not rated it
     */
    public ShelfEntryResponse toResponse(
        final ShelfEntry entry, final Book book, final @Nullable Integer myRating) {
        return new ShelfEntryResponse(
            book.getDedupKey(),
            book.getTitle(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            coverImages.servedUrl(book.getDedupKey(), book.getCoverUrl()),
            entry.getStatus(),
            entry.isFavorite(),
            entry.getStartedAt(),
            entry.getFinishedAt(),
            entry.getNotes(),
            entry.getCreatedAt().toLocalDate(),
            book.getAverageRating(),
            myRating);
    }
}
