package com.betterreads.features.metadatacheck;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MetadataCheckRepository extends JpaRepository<Book, Long> {

    @EntityGraph(attributePaths = "authors")
    @Query("""
        SELECT b FROM Book b
        WHERE b.metadataCheckedAt IS NULL AND b.createdAt >= :since
        ORDER BY b.createdAt ASC
        """)
    List<Book> findUncheckedSince(@Param("since") OffsetDateTime since, Pageable pageable);

    @Query("SELECT DISTINCT b.seriesName FROM Book b WHERE b.seriesName IS NOT NULL")
    List<String> findSeriesNames();
}
