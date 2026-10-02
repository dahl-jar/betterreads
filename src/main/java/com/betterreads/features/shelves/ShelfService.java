package com.betterreads.features.shelves;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** One user's reading shelf: status, favorite flag, reading dates and notes per book. */
interface ShelfService {

    /**
     * Shelves the book on first call.
     *
     * @throws com.betterreads.errors.ResourceNotFoundException if no book has the key
     */
    ShelfEntryResponse changeStatus(Long userId, String bookKey, ReadingStatus status);

    /**
     * Favoriting an unshelved book shelves it at WANT_TO_READ.
     *
     * @throws com.betterreads.errors.ResourceNotFoundException if no book has the key
     */
    ShelfEntryResponse markFavorite(Long userId, String bookKey, boolean favorite);

    /**
     * Null request fields keep their stored value.
     *
     * @throws com.betterreads.errors.ResourceNotFoundException if the book is not on the shelf
     * @throws com.betterreads.errors.InvalidRequestException if the finished date is before the
     *     started date
     */
    ShelfEntryResponse updateEntry(Long userId, String bookKey, UpdateEntryRequest request);

    /** Removing a book that is not on the shelf does nothing. */
    void remove(Long userId, String bookKey);

    /** Newest first, every status when status is null. */
    List<ShelfEntryResponse> list(Long userId, @Nullable ReadingStatus status);

    ShelfCountsResponse countsForBook(String bookKey);
}
