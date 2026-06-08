package com.betterreads.catalog.repository;

import java.util.List;

import com.betterreads.catalog.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Catalog reads for the homepage book lists. */
public interface BookListRepository extends JpaRepository<Book, Long> {

    /** Returns the most recently added books, authors fetched, newest first, capped by the pageable. */
    @EntityGraph(attributePaths = "authors")
    @Query("SELECT b FROM Book b ORDER BY b.createdAt DESC")
    List<Book> findRecentlyAdded(Pageable pageable);

    /**
     * Returns the best-rated books with more than {@code minRatingCount} ratings, authors fetched,
     * highest average first, capped by the pageable.
     */
    @EntityGraph(attributePaths = "authors")
    @Query("SELECT b FROM Book b WHERE b.ratingCount > :minRatingCount ORDER BY b.averageRating DESC")
    List<Book> findTopRated(@Param("minRatingCount") int minRatingCount, Pageable pageable);
}
