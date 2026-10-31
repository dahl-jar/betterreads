package com.betterreads.features.descriptionbackfill;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface BookDescriptionRepository extends JpaRepository<Book, Long> {

    @Query("""
        SELECT b.bookId FROM Book b
        WHERE (b.description IS NULL OR LENGTH(b.description) < :minLength)
          AND (b.wikidataQid IS NOT NULL OR b.isbn IS NOT NULL)
          AND CAST(b.verifiedFields AS String) NOT LIKE '%DESCRIPTION%'
        ORDER BY b.descriptionCheckedAt ASC NULLS FIRST, b.updatedAt ASC, b.bookId ASC
        """)
    List<Long> findIdsWithThinDescription(int minLength, Pageable pageable);

    @Query("""
        SELECT b.bookId FROM Book b
        WHERE (b.wikidataQid IS NOT NULL OR b.isbn IS NOT NULL)
          AND CAST(b.verifiedFields AS String) NOT LIKE '%DESCRIPTION%'
        ORDER BY b.bookId ASC
        """)
    List<Long> findIdsKeyed(Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
        UPDATE Book b
        SET b.description = :description, b.updatedAt = :checkedAt, b.descriptionCheckedAt = :checkedAt
        WHERE b.bookId = :bookId AND CAST(b.verifiedFields AS String) NOT LIKE '%DESCRIPTION%'
        """)
    void updateDescription(
        @Param("bookId") long bookId,
        @Param("description") String description,
        @Param("checkedAt") OffsetDateTime checkedAt);

    @Transactional
    @Modifying
    @Query("UPDATE Book b SET b.descriptionCheckedAt = :checkedAt WHERE b.bookId = :bookId")
    void markDescriptionChecked(@Param("bookId") long bookId, @Param("checkedAt") OffsetDateTime checkedAt);
}
