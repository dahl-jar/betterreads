package com.betterreads.features.metadatacheck;

import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MetadataCheckRepository extends JpaRepository<Book, Long> {

    @EntityGraph(attributePaths = {"authors", "series"})
    @Query("""
        SELECT b FROM Book b
        WHERE b.metadataCheckRequestedAt IS NOT NULL
        ORDER BY b.metadataCheckRequestedAt ASC, b.bookId ASC
        """)
    List<Book> findDueForCheck(Pageable pageable);

    @Query(value = """
        SELECT series_name FROM book WHERE series_name IS NOT NULL
        UNION
        SELECT series_name FROM book_series
        """, nativeQuery = true)
    List<String> findSeriesNames();

    @Query("SELECT a.name FROM Author a")
    List<String> findAuthorNames();
}
