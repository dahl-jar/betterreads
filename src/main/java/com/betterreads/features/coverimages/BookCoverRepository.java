package com.betterreads.features.coverimages;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.book.Book;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Book cover reads and writes. */
interface BookCoverRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByDedupKey(String dedupKey);

    /** An unmirrorable cover keeps a null object key, so only books not checked since {@code runStart} come back. */
    @Query("""
        SELECT b FROM Book b
        WHERE b.coverUrl IS NOT NULL AND b.coverObjectKey IS NULL
          AND (b.coverCheckedAt IS NULL OR b.coverCheckedAt < :runStart)
        ORDER BY b.coverCheckedAt ASC NULLS FIRST, b.updatedAt ASC
        """)
    List<Book> findCoverSweepCandidates(OffsetDateTime runStart, Pageable pageable);

    /** Skips the rating columns, so a slow mirror cannot write back a stale rating or community aggregate. */
    @Transactional
    @Modifying
    @Query("""
        UPDATE Book b
        SET b.coverObjectKey = :objectKey, b.coverCheckedAt = :checkedAt, b.updatedAt = :checkedAt
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

    @Query("""
        SELECT b FROM Book b
        WHERE b.coverSearchedAt IS NULL
           OR (b.coverSearchedAt < :searchedBefore
               AND (b.coverSource IS NULL OR b.coverSource <> com.betterreads.booksource.CoverSource.APPLE_BOOKS))
        ORDER BY CASE WHEN b.coverUrl LIKE 'https://books.google.com/%' THEN 0 ELSE 1 END,
          b.coverSearchedAt ASC NULLS FIRST, b.bookId ASC
        """)
    List<Book> findUpgradeCandidates(@Param("searchedBefore") OffsetDateTime searchedBefore, Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
        UPDATE Book b
        SET b.coverUrl = :#{#cover.url()},
            b.coverSource = :#{#cover.source()},
            b.coverStoreUrl = :#{#cover.storeUrl()},
            b.coverObjectKey = :objectKey, b.coverCheckedAt = :at, b.coverSearchedAt = :at, b.updatedAt = :at
        WHERE b.bookId = :bookId
        """)
    void applyCover(
        @Param("bookId") long bookId,
        @Param("cover") CoverCandidate cover,
        @Param("objectKey") String objectKey,
        @Param("at") OffsetDateTime at);

    @Transactional
    @Modifying
    @Query("""
        UPDATE Book b
        SET b.coverUrl = NULL, b.coverSource = NULL, b.coverStoreUrl = NULL, b.coverObjectKey = NULL,
            b.coverCheckedAt = :at, b.coverSearchedAt = :at, b.updatedAt = :at
        WHERE b.bookId = :bookId
        """)
    void clearCover(@Param("bookId") long bookId, @Param("at") OffsetDateTime at);

    @Transactional
    @Modifying
    @Query("UPDATE Book b SET b.coverSearchedAt = :at WHERE b.bookId = :bookId")
    void saveCoverSearchedAt(@Param("bookId") long bookId, @Param("at") @Nullable OffsetDateTime at);
}
