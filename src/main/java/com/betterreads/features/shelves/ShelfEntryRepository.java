package com.betterreads.features.shelves;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ShelfEntryRepository extends JpaRepository<ShelfEntry, Long> {

    Optional<ShelfEntry> findByUserIdAndBookId(Long userId, Long bookId);

    List<ShelfEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ShelfEntry> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReadingStatus status);

    void deleteByUserIdAndBookId(Long userId, Long bookId);

    @Query("""
        SELECT e
        FROM ShelfEntry e
        JOIN User u ON u.userId = e.userId
        WHERE e.userId IN :userIds AND e.bookId IN :bookIds
        """)
    List<ShelfEntry> findForActiveReaders(
        @Param("userIds") Collection<Long> userIds, @Param("bookIds") Collection<Long> bookIds);

    @Query("""
        SELECT new com.betterreads.features.shelves.StatusCount(e.status, COUNT(e))
        FROM ShelfEntry e
        JOIN User u ON u.userId = e.userId
        WHERE e.bookId = :bookId
        GROUP BY e.status
        """)
    List<StatusCount> countByStatusForBook(@Param("bookId") Long bookId);

    long countByUserId(Long userId);
}
