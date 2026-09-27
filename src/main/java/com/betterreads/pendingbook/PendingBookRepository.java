package com.betterreads.pendingbook;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingBookRepository extends JpaRepository<PendingBook, Long> {

    /**
     * Two concurrent stages of the same book would hit a duplicate-key error, so an existing row is
     * left alone.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO pending_book (dedup_key, status) VALUES (:dedupKey, 'PENDING') "
        + "ON CONFLICT (dedup_key) DO NOTHING", nativeQuery = true)
    void reserve(@Param("dedupKey") String dedupKey);

    Optional<PendingBook> findByDedupKey(String dedupKey);

    Optional<PendingBook> findByIsbn13(String isbn13);

    Optional<PendingBook> findByOpenLibraryWorkKey(String openLibraryWorkKey);

    /** Oldest first. */
    @Query("SELECT p FROM PendingBook p WHERE p.status = 'PENDING' "
        + "AND (p.lastAttemptAt IS NULL OR p.lastAttemptAt < :cutoff) ORDER BY p.firstSeenAt ASC")
    List<PendingBook> findDue(@Param("cutoff") OffsetDateTime cutoff);
}
