package com.betterreads.bookindex;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.betterreads.booksource.CreditRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorIndexViewReader {

    private static final String VIEW_SQL = """
        SELECT
            author.author_id,
            author.name,
            author.photo_url,
            count(*) AS book_count,
            coalesce(sum(log(1 + coalesce(book.rating_count, 0)) * coalesce(book.average_rating, 0)), 0)
                AS popularity_score,
            (array_agg(book.title ORDER BY book.rating_count DESC NULLS LAST, book.book_id))[1:5] AS top_titles,
            ARRAY(
                SELECT author_merge.merged_name
                FROM author_merge
                WHERE author_merge.author_id = author.author_id
                ORDER BY author_merge.merged_author_id
            ) AS aliases
        FROM author
        INNER JOIN book_author
            ON book_author.author_id = author.author_id
            AND book_author.role IN\s""" + CreditRole.PRIMARY_SQL + """

        INNER JOIN book ON book.book_id = book_author.book_id
        """;

    private static final String PAGE_SQL = VIEW_SQL + """
        WHERE author.author_id > ?
        GROUP BY author.author_id
        ORDER BY author.author_id
        LIMIT ?
        """;

    private static final String BY_IDS_SQL = VIEW_SQL + """
        WHERE author.author_id = ANY (?)
        GROUP BY author.author_id
        ORDER BY author.author_id
        """;

    private static final String IDS_OF_BOOK_SQL = """
        SELECT book_author.author_id
        FROM book_author
        INNER JOIN book ON book.book_id = book_author.book_id
        WHERE book.dedup_key = ?
        """;

    private static final String IDS_CHANGED_SQL = """
        SELECT author.author_id
        FROM author
        WHERE author.updated_at >= ?
        UNION
        SELECT book_author.author_id
        FROM book_author
        INNER JOIN book ON book.book_id = book_author.book_id
        WHERE book.updated_at >= ?
        """;

    private static final String WITH_PRIMARY_CREDIT_SQL = """
        SELECT DISTINCT book_author.author_id
        FROM book_author
        WHERE book_author.author_id = ANY (?)
            AND book_author.role IN\s""" + CreditRole.PRIMARY_SQL;

    private final JdbcTemplate jdbc;

    public AuthorIndexViewReader(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AuthorIndexView> page(final long afterAuthorId, final int size) {
        return jdbc.query(PAGE_SQL, (row, index) -> view(row), afterAuthorId, size);
    }

    @Transactional(readOnly = true)
    public List<AuthorIndexView> forAuthors(final Collection<Long> authorIds) {
        return jdbc.query(BY_IDS_SQL, (row, index) -> view(row), (Object) authorIds.toArray(Long[]::new));
    }

    @Transactional(readOnly = true)
    public Set<Long> withPrimaryCredit(final Collection<Long> authorIds) {
        return Set.copyOf(jdbc.queryForList(WITH_PRIMARY_CREDIT_SQL, Long.class,
            (Object) authorIds.toArray(Long[]::new)));
    }

    @Transactional(readOnly = true)
    public List<Long> authorIdsOfBook(final String dedupKey) {
        return jdbc.queryForList(IDS_OF_BOOK_SQL, Long.class, dedupKey);
    }

    @Transactional(readOnly = true)
    public List<Long> authorIdsChangedSince(final OffsetDateTime since) {
        return jdbc.queryForList(IDS_CHANGED_SQL, Long.class, since, since);
    }

    private static AuthorIndexView view(final ResultSet row) throws SQLException {
        return new AuthorIndexView(
            row.getLong("author_id"),
            row.getString("name"),
            strings(row.getArray("aliases")),
            row.getString("photo_url"),
            row.getInt("book_count"),
            row.getDouble("popularity_score"),
            strings(row.getArray("top_titles")));
    }

    private static List<String> strings(final Array array) throws SQLException {
        return Arrays.stream((Object[]) array.getArray()).map(String.class::cast).toList();
    }
}
