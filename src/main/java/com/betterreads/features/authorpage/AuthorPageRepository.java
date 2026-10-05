package com.betterreads.features.authorpage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuthorPageRepository {

    private static final String AUTHOR_SQL = "SELECT author_id, name, photo_url, bio FROM author WHERE author_id = ?";

    private static final String BOOKS_SQL = """
        SELECT
            book.dedup_key,
            book.title,
            book.cover_url,
            book.first_publish_year,
            book.series_name,
            book.series_position,
            book_author.role
        FROM book_author
        INNER JOIN book ON book.book_id = book_author.book_id
        WHERE book_author.author_id = ?
        ORDER BY book.series_name NULLS LAST, book.series_position NULLS LAST,
            book.first_publish_year NULLS LAST, book.title
        """;

    private final JdbcTemplate jdbc;

    AuthorPageRepository(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AuthorRow> findAuthor(final long authorId) {
        return jdbc.query(AUTHOR_SQL, (row, index) -> new AuthorRow(
                row.getLong("author_id"), row.getString("name"), row.getString("photo_url"), row.getString("bio")),
                authorId)
            .stream()
            .findFirst();
    }

    List<BookRow> findBooks(final long authorId) {
        return jdbc.query(BOOKS_SQL, (row, index) -> new BookRow(
            row.getString("dedup_key"),
            row.getString("title"),
            row.getString("cover_url"),
            row.getObject("first_publish_year", Integer.class),
            row.getString("series_name"),
            row.getBigDecimal("series_position"),
            row.getString("role")), authorId);
    }

    record AuthorRow(long authorId, String name, @Nullable String photoUrl, @Nullable String bio) {
    }

    record BookRow(
        String dedupKey,
        String title,
        @Nullable String coverUrl,
        @Nullable Integer firstPublishYear,
        @Nullable String seriesName,
        @Nullable BigDecimal seriesPosition,
        String role
    ) {
    }
}
