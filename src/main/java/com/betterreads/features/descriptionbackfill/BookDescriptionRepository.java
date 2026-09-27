package com.betterreads.features.descriptionbackfill;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Book reads and description writes for the description backfill. */
interface BookDescriptionRepository extends JpaRepository<Book, Long> {

    /**
     * Wikipedia and Apple Books need a Wikidata QID or an ISBN, so only books with one qualify. Least
     * recently checked first, so a book the sources cannot improve does not block the ones behind it.
     * Authors are fetched for the Apple Books title and author search.
     */
    @EntityGraph(attributePaths = "authors")
    @Query("""
        SELECT b FROM Book b
        WHERE (b.description IS NULL OR LENGTH(b.description) < :minLength)
          AND (b.wikidataQid IS NOT NULL OR b.isbn IS NOT NULL)
        ORDER BY b.descriptionCheckedAt ASC NULLS FIRST, b.updatedAt ASC
        """)
    List<Book> findThinDescriptions(int minLength, Pageable pageable);

    @EntityGraph(attributePaths = "authors")
    @Query("""
        SELECT b FROM Book b
        WHERE b.wikidataQid IS NOT NULL OR b.isbn IS NOT NULL
        ORDER BY b.bookId ASC
        """)
    List<Book> findAllKeyedBooks(Pageable pageable);

    /**
     * The description comes from slow external calls, and {@code save(book)} would overwrite a rating or
     * community aggregate committed meanwhile, so only the description and check time are written.
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

    /** Lets the next run move past a book the sources could not improve. */
    @Transactional
    @Modifying
    @Query("UPDATE Book b SET b.descriptionCheckedAt = :checkedAt WHERE b.bookId = :bookId")
    void markDescriptionChecked(@Param("bookId") long bookId, @Param("checkedAt") OffsetDateTime checkedAt);
}
