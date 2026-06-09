package com.betterreads.catalog.repository;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.catalog.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Catalog reads and targeted description writes for the backfill, which re-resolves stored descriptions. */
public interface BookDescriptionRepository extends JpaRepository<Book, Long> {

    /**
     * Returns books with a thin description and a key a description source can resolve, least recently
     * checked first, for the backfill to re-resolve a slice at a time.
     *
     * <p>A book qualifies when its description is short and it carries a Wikidata QID or an ISBN, the
     * keys Wikipedia and Apple Books need. Ordering by {@code descriptionCheckedAt} with nulls first
     * walks every candidate once before any is rechecked, so a book the sources cannot improve does
     * not block the ones behind it. Authors are fetched for the Apple Books title-author fallback.
     */
    @EntityGraph(attributePaths = "authors")
    @Query("""
        SELECT b FROM Book b
        WHERE (b.description IS NULL OR LENGTH(b.description) < :minLength)
          AND (b.wikidataQid IS NOT NULL OR b.isbn IS NOT NULL)
        ORDER BY b.descriptionCheckedAt ASC NULLS FIRST, b.updatedAt ASC
        """)
    List<Book> findDescriptionBackfillCandidates(int minLength, Pageable pageable);

    /**
     * Returns books with a key a description source can resolve, ordered by id, for the one-time
     * sweep that re-resolves every book's description through the description sources.
     */
    @EntityGraph(attributePaths = "authors")
    @Query("""
        SELECT b FROM Book b
        WHERE b.wikidataQid IS NOT NULL OR b.isbn IS NOT NULL
        ORDER BY b.bookId ASC
        """)
    List<Book> findAllKeyedBooks(Pageable pageable);

    /**
     * Writes a new description and stamps the check time for one book, touching only those columns.
     *
     * <p>A targeted update rather than {@code save(book)} so a description resolved from slow external
     * calls cannot merge a stale rating or community aggregate that another write committed meanwhile.
     */
    @Transactional
    @Modifying
    @Query("""
        UPDATE Book b
        SET b.description = :description, b.updatedAt = :checkedAt, b.descriptionCheckedAt = :checkedAt
        WHERE b.bookId = :bookId
        """)
    void updateDescription(
        @Param("bookId") long bookId,
        @Param("description") String description,
        @Param("checkedAt") OffsetDateTime checkedAt);

    /** Stamps the check time for a book the sources could not improve, so it yields to the ones behind it. */
    @Transactional
    @Modifying
    @Query("UPDATE Book b SET b.descriptionCheckedAt = :checkedAt WHERE b.bookId = :bookId")
    void markDescriptionChecked(@Param("bookId") long bookId, @Param("checkedAt") OffsetDateTime checkedAt);
}
