package com.betterreads.catalog.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Catalog reads and targeted cover writes for mirroring book covers into object storage. */
public interface BookCoverRepository extends JpaRepository<Book, Long> {

    /** Returns the promoted book with the given dedup key, for the mirror to read its source cover. */
    Optional<Book> findByDedupKey(String dedupKey);

    /**
     * Returns promoted books whose cover is not yet mirrored, least recently checked first, for the
     * backfill to mirror a slice at a time.
     *
     * <p>A book qualifies when it has a source cover URL but no stored object key. Ordering by
     * {@code coverCheckedAt} with nulls first walks every candidate once before any is rechecked, so
     * a cover the sources cannot mirror does not block the ones behind it.
     */
    @Query("""
        SELECT b FROM Book b
        WHERE b.coverUrl IS NOT NULL AND b.coverObjectKey IS NULL
        ORDER BY b.coverCheckedAt ASC NULLS FIRST, b.updatedAt ASC
        """)
    List<Book> findCoverBackfillCandidates(Pageable pageable);

    /**
     * Returns un-mirrored books not yet visited in this sweep, least recently checked first, so the
     * full sweep terminates: a book the sources cannot mirror keeps its null object key but its check
     * time advances past {@code runStart}, dropping it from later slices of the same run.
     */
    @Query("""
        SELECT b FROM Book b
        WHERE b.coverUrl IS NOT NULL AND b.coverObjectKey IS NULL
          AND (b.coverCheckedAt IS NULL OR b.coverCheckedAt < :runStart)
        ORDER BY b.coverCheckedAt ASC NULLS FIRST, b.updatedAt ASC
        """)
    List<Book> findCoverSweepCandidates(OffsetDateTime runStart, Pageable pageable);

    /**
     * Records the stored object key and stamps the check time for one book, touching only those
     * columns so a slow mirror cannot merge a stale rating or community aggregate.
     */
    @Transactional
    @Modifying
    @Query("""
        UPDATE Book b
        SET b.coverObjectKey = :objectKey, b.coverCheckedAt = :checkedAt
        WHERE b.bookId = :bookId
        """)
    void markCoverMirrored(
        @Param("bookId") long bookId,
        @Param("objectKey") String objectKey,
        @Param("checkedAt") OffsetDateTime checkedAt);

    /** Stamps the check time for a book whose cover could not be mirrored, so it yields to the rest. */
    @Transactional
    @Modifying
    @Query("UPDATE Book b SET b.coverCheckedAt = :checkedAt WHERE b.bookId = :bookId")
    void markCoverChecked(@Param("bookId") long bookId, @Param("checkedAt") OffsetDateTime checkedAt);
}
