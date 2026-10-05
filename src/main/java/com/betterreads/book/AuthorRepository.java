package com.betterreads.book;

import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.CreditRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByNameKey(String nameKey);

    Optional<Author> findByWikidataQid(String wikidataQid);

    @Query(value = "SELECT author_id FROM author_merge WHERE merged_author_id = :mergedAuthorId", nativeQuery = true)
    Optional<Long> findMergedInto(long mergedAuthorId);

    @Query(value = """
        SELECT DISTINCT author.name
        FROM author
        INNER JOIN book_author ON book_author.author_id = author.author_id
        WHERE book_author.role IN\s""" + CreditRole.PRIMARY_SQL, nativeQuery = true)
    List<String> findPrimaryCreditNames();

    @Transactional
    @Modifying
    @Query(value = """
        DELETE FROM author
        WHERE NOT EXISTS (SELECT 1 FROM book_author WHERE book_author.author_id = author.author_id)
        """, nativeQuery = true)
    int deleteUncredited();
}
