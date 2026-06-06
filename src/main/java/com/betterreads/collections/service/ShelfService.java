package com.betterreads.collections.service;

import java.util.List;

import com.betterreads.collections.dto.ShelfEntryResponse;
import com.betterreads.collections.dto.UpdateEntryRequest;
import com.betterreads.collections.entity.ReadingStatus;

import org.jspecify.annotations.Nullable;

/**
 * The reading shelf for one user: the books they want to read, are reading, finished, or dropped,
 * plus favorites and reading notes. Every operation is scoped to the acting user, identified by the
 * {@code userId} from the access token.
 */
public interface ShelfService {

    /**
     * Moves the book to the given shelf status, creating the entry on first call. Entering
     * {@link ReadingStatus#CURRENTLY_READING} or {@link ReadingStatus#FINISHED} stamps the matching
     * reading date.
     *
     * @throws com.betterreads.common.exception.ResourceNotFoundException if no book has the key
     */
    ShelfEntryResponse changeStatus(Long userId, String bookKey, ReadingStatus status);

    /**
     * Marks the book a favorite or clears the flag, creating the entry at
     * {@link ReadingStatus#WANT_TO_READ} on first call. Favorite is independent of status.
     *
     * @throws com.betterreads.common.exception.ResourceNotFoundException if no book has the key
     */
    ShelfEntryResponse markFavorite(Long userId, String bookKey, boolean favorite);

    /**
     * Updates the reading dates and note on an existing entry.
     *
     * @throws com.betterreads.common.exception.ResourceNotFoundException if the book is not on the
     *     shelf
     * @throws com.betterreads.common.exception.InvalidRequestException if the finished date is before
     *     the started date
     */
    ShelfEntryResponse updateEntry(Long userId, String bookKey, UpdateEntryRequest request);

    /** Removes the book from the shelf. A book not on the shelf is a no-op. */
    void remove(Long userId, String bookKey);

    /** Returns the user's shelf, optionally filtered to one status, newest first. */
    List<ShelfEntryResponse> list(Long userId, @Nullable ReadingStatus status);
}
