package com.betterreads.features.coverimages;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Book reads and cover writes for the cover mirror. */
interface BookCoverRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByDedupKey(String dedupKey);

    /**
     * A cover the sources cannot mirror would block the books behind it, so the least recently
     * checked come first. It also keeps its null object key, so only books not checked since
     * {@code runStart} come back and the full sweep ends.
     */
    @Query("""
        SELECT b FROM Book b
        WHERE b.coverUrl IS NOT NULL AND b.coverObjectKey IS NULL
          AND (b.coverCheckedAt IS NULL OR b.coverCheckedAt < :runStart)
        ORDER BY b.coverCheckedAt ASC NULLS FIRST, b.updatedAt ASC
        """)
    List<Book> findCoverSweepCandidates(OffsetDateTime runStart, Pageable pageable);

    /** Writes only the cover columns, so a slow mirror cannot write back a stale rating or community aggregate. */
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

    /** A cover that could not be mirrored gets a check time, so it moves behind the rest. */
    @Transactional
    @Modifying
    @Query("UPDATE Book b SET b.coverCheckedAt = :checkedAt WHERE b.bookId = :bookId")
    void markCoverChecked(@Param("bookId") long bookId, @Param("checkedAt") OffsetDateTime checkedAt);
}
