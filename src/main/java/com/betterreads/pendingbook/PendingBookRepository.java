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
    @Query(value = "SELECT * FROM pending_book WHERE status = 'PENDING' AND (last_attempt_at IS NULL "
        + "OR last_attempt_at < CAST(:now AS timestamptz) - CASE "
        + "WHEN attempt_count <= 1 THEN INTERVAL '15 minutes' "
        + "WHEN attempt_count = 2 THEN INTERVAL '1 hour' "
        + "WHEN attempt_count = 3 THEN INTERVAL '6 hours' "
        + "ELSE INTERVAL '24 hours' END) ORDER BY first_seen_at ASC", nativeQuery = true)
    List<PendingBook> findDue(@Param("now") OffsetDateTime now);
}
