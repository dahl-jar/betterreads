package com.betterreads.features.catalogrefresh;

import java.time.OffsetDateTime;
import java.util.List;

import com.betterreads.book.Book;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface SeriesRefreshRepository extends Repository<Book, Long> {

    @Query(value = """
        SELECT b.series_name FROM book b
        LEFT JOIN series_refresh r ON r.series_name = b.series_name
        WHERE b.series_name IS NOT NULL AND b.verified_fields NOT LIKE '%SERIES%'
        GROUP BY b.series_name, r.refreshed_at
        ORDER BY r.refreshed_at ASC NULLS FIRST, b.series_name
        """, nativeQuery = true)
    List<String> findSeriesDueForRefresh();

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO series_refresh (series_name, refreshed_at) VALUES (:seriesName, :refreshedAt)
        ON CONFLICT (series_name) DO UPDATE SET refreshed_at = EXCLUDED.refreshed_at
        """, nativeQuery = true)
    void markRefreshed(@Param("seriesName") String seriesName, @Param("refreshedAt") OffsetDateTime refreshedAt);
}
