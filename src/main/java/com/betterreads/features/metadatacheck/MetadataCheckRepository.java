package com.betterreads.features.metadatacheck;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.book.Book;
import com.betterreads.clients.websearch.SeriesBook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MetadataCheckRepository extends JpaRepository<Book, Long> {

    @EntityGraph(attributePaths = {"credits", "credits.author", "series", "subjects"})
    @Query("""
        SELECT b FROM Book b
        WHERE b.metadataCheckRequestedAt IS NOT NULL
          AND b.metadataCheckRequestedAt <= :now
        ORDER BY b.metadataCheckRequestedAt ASC, b.bookId ASC
        """)
    List<Book> findDueForCheck(@Param("now") OffsetDateTime now, Pageable pageable);

    @Query(value = """
        SELECT series_name FROM book WHERE series_name IS NOT NULL
        UNION
        SELECT series_name FROM book_series
        """, nativeQuery = true)
    List<String> findSeriesNames();

    @Query("""
        SELECT new com.betterreads.clients.websearch.SeriesBook(b.title, s.position)
        FROM Book b JOIN b.series s
        WHERE s.name = :seriesName AND b.bookId <> :excludedBookId
        ORDER BY s.position, b.bookId
        LIMIT 30
        """)
    List<SeriesBook> findSeriesBooks(
        @Param("seriesName") String seriesName, @Param("excludedBookId") long excludedBookId);

    @Query("SELECT a.name FROM Author a")
    List<String> findAuthorNames();
}
