package com.betterreads.collections.dto;

import com.betterreads.collections.entity.ReadingStatus;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A book on the user's shelf, returned by the shelf endpoints. Carries the book summary the shelf UI
 * shows plus the shelf state.
 *
 * @param key the book's public key, shared with search and detail
 * @param title the book title
 * @param authors the author names
 * @param coverUrl the cover image URL, null when the book has no cover
 * @param status the shelf the book sits on
 * @param favorite whether the book is flagged a favorite
 * @param startedAt the date reading began, null when unset
 * @param finishedAt the date reading finished, null when unset
 * @param notes the user's private note, null when unset
 */
public record ShelfEntryResponse(
    String key,
    String title,
    List<String> authors,
    @Nullable String coverUrl,
    ReadingStatus status,
    boolean favorite,
    @Nullable LocalDate startedAt,
    @Nullable LocalDate finishedAt,
    @Nullable String notes
) {

    public ShelfEntryResponse {
        authors = List.copyOf(authors);
    }

    @Override
    public List<String> authors() {
        return List.copyOf(authors);
    }
}
