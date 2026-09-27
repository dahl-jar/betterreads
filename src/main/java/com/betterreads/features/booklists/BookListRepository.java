package com.betterreads.features.booklists;

import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Catalog reads for the homepage book lists. */
interface BookListRepository extends JpaRepository<Book, Long> {

    @EntityGraph(attributePaths = "authors")
    @Query("SELECT b FROM Book b ORDER BY b.createdAt DESC")
    List<Book> findRecentlyAdded(Pageable pageable);

    @EntityGraph(attributePaths = "authors")
    @Query("SELECT b FROM Book b WHERE b.ratingCount > :ratingFloor ORDER BY b.averageRating DESC")
    List<Book> findTopRated(@Param("ratingFloor") int ratingFloor, Pageable pageable);
}
