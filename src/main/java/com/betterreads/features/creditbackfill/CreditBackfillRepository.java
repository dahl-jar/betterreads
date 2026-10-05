package com.betterreads.features.creditbackfill;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface CreditBackfillRepository extends JpaRepository<Book, Long> {

    @Query(value = """
        SELECT *
        FROM book
        WHERE hardcover_id IS NOT NULL
            AND credits_checked_at IS NULL
        ORDER BY rating_count DESC NULLS LAST, book_id
        """, nativeQuery = true)
    List<Book> findUncheckedCredits(Pageable pageable);

    @Transactional
    @Modifying
    @Query(value = "UPDATE book SET credits_checked_at = :checkedAt WHERE book_id = :bookId", nativeQuery = true)
    void markCreditsChecked(@Param("bookId") long bookId, @Param("checkedAt") OffsetDateTime checkedAt);
}
