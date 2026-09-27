package com.betterreads.features.shelves;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ShelfEntryRepository extends JpaRepository<ShelfEntry, Long> {

    Optional<ShelfEntry> findByUserIdAndBookId(Long userId, Long bookId);

    List<ShelfEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ShelfEntry> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReadingStatus status);

    void deleteByUserIdAndBookId(Long userId, Long bookId);
}
