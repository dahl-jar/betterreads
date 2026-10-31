package com.betterreads.features.booklists;

import java.util.List;
import java.util.Optional;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookListRepository extends JpaRepository<Book, Long> {

    @Query("SELECT b.bookId FROM Book b ORDER BY b.createdAt DESC, b.bookId DESC")
    List<Long> findIdsRecentlyAdded(Pageable pageable);

    @Query("""
        SELECT b.bookId FROM Book b
        WHERE b.ratingCount > :ratingFloor
        ORDER BY b.averageRating DESC, b.bookId DESC
        """)
    List<Long> findIdsTopRated(@Param("ratingFloor") int ratingFloor, Pageable pageable);

    @EntityGraph(attributePaths = "series")
    Optional<Book> findWithSeriesByDedupKey(String dedupKey);

    @Query("""
        SELECT new com.betterreads.features.booklists.SeriesPosition(b.bookId, s.position)
        FROM Book b JOIN b.series s
        WHERE s.name = :seriesName AND b.bookId <> :bookId
        ORDER BY s.position, b.bookId
        """)
    List<SeriesPosition> findOthersInSeries(
        @Param("seriesName") String seriesName, @Param("bookId") Long bookId, Pageable pageable);
}
