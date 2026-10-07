package com.betterreads.features.search;

import java.util.Collection;
import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface BookSearchRepository extends Repository<Book, Long> {

    @Query("SELECT b.bookId FROM Book b WHERE b.isbn = :isbn ORDER BY b.bookId")
    List<Long> findIdsByIsbn(@Param("isbn") String isbn, Pageable page);

    @Query("SELECT b.bookId FROM Book b WHERE lower(b.title) = lower(:title) ORDER BY b.bookId")
    List<Long> findIdsByTitleIgnoreCase(@Param("title") String title, Pageable page);

    @Query("SELECT b.dedupKey FROM Book b WHERE b.dedupKey > :afterKey ORDER BY b.dedupKey")
    List<String> findDedupKeysAfter(@Param("afterKey") String afterKey, Pageable page);

    @Query("SELECT b.bookId FROM Book b WHERE b.dedupKey IN :keys")
    List<Long> findIdsByDedupKeyIn(@Param("keys") Collection<String> keys);
}
