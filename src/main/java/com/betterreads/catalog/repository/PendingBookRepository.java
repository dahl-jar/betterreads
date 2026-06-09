package com.betterreads.catalog.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.PendingBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link PendingBook}. */
public interface PendingBookRepository extends JpaRepository<PendingBook, Long> {

    /**
     * Reserves the row for {@code dedupKey}, doing nothing when it already exists. Two concurrent
     * stages of the same book both call this; one inserts, the other is a no-op, so the row exists
     * exactly once before either fills in the descriptive fields. This is the atomic step that makes
     * staging race-safe without catching duplicate-key errors.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO pending_book (dedup_key, status) VALUES (:dedupKey, 'PENDING') "
        + "ON CONFLICT (dedup_key) DO NOTHING", nativeQuery = true)
    void reserve(@Param("dedupKey") String dedupKey);

    Optional<PendingBook> findByDedupKey(String dedupKey);

    Optional<PendingBook> findByIsbn13(String isbn13);

    Optional<PendingBook> findByOpenLibraryWorkKey(String openLibraryWorkKey);

    Optional<PendingBook> findByGoogleBooksVolumeId(String googleBooksVolumeId);

    Optional<PendingBook> findByHardcoverId(String hardcoverId);

    Optional<PendingBook> findByLocLccn(String locLccn);

    Optional<PendingBook> findByWikidataQid(String wikidataQid);

    /** Returns PENDING candidates not attempted since {@code cutoff}, oldest first, for the poll. */
    @Query("SELECT p FROM PendingBook p WHERE p.status = 'PENDING' "
        + "AND (p.lastAttemptAt IS NULL OR p.lastAttemptAt < :cutoff) ORDER BY p.firstSeenAt ASC")
    List<PendingBook> findPendingNotAttemptedSince(@Param("cutoff") OffsetDateTime cutoff);
}
