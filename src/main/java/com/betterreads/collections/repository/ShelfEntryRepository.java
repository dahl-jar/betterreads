package com.betterreads.collections.repository;

import java.util.List;
import java.util.Optional;

import com.betterreads.collections.entity.ReadingStatus;
import com.betterreads.collections.entity.ShelfEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link ShelfEntry}, scoped to one user's shelf. */
public interface ShelfEntryRepository extends JpaRepository<ShelfEntry, Long> {

    /** Returns the user's entry for the book, or empty when the book is not on the shelf. */
    Optional<ShelfEntry> findByUserIdAndBookId(Long userId, Long bookId);

    /** Returns the user's shelf, newest first. */
    List<ShelfEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Returns the user's shelf at the given status, newest first. */
    List<ShelfEntry> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReadingStatus status);

    /** Removes the user's entry for the book. A book not on the shelf is a no-op. */
    void deleteByUserIdAndBookId(Long userId, Long bookId);
}
