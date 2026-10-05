package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.assertj.core.groups.Tuple;
import com.betterreads.testsupport.Migrations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class V48MergeDuplicateAuthorsIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String BEFORE = "46";

    private static final String AFTER = "48";

    private static final String KING = "Stephen King";

    private static final String SHOUTING_KING = "STEPHEN KING";

    private static final String CARRIE = "Carrie";

    private static final String MISERY = "Misery";

    private static final String TOLKIEN = "J.R.R. Tolkien";

    private static final String AUTHOR_ROLE = "AUTHOR";

    private static final String AUTHOR_IDS_SQL = "SELECT author_id FROM author";

    private static final String SET_QID_SQL = "UPDATE author SET wikidata_qid = 'Q39829' WHERE author_id = ?";

    private static final String CREDITS_SQL = """
        SELECT author.name, book_author.role
        FROM book_author
        INNER JOIN author ON author.author_id = book_author.author_id
        WHERE book_author.book_id = ?
        ORDER BY book_author.position
        """;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = Migrations.resetTo(POSTGRES, BEFORE);
    }

    @Nested
    class Merging {

        @Test
        void shouldKeepOneAuthorPerKey() {
            final long survivor = author(KING);
            credit(book("It"), survivor);
            credit(book(CARRIE), survivor);
            final long shouting = author(SHOUTING_KING);
            final long misery = book(MISERY);
            credit(misery, shouting);
            final long flipped = author("King, Stephen");
            final long joyland = book("Joyland");
            credit(joyland, flipped);

            Migrations.migrateTo(POSTGRES, AFTER);

            assertThat(jdbc.queryForList(AUTHOR_IDS_SQL, Long.class)).containsExactly(survivor);
            assertThat(jdbc.queryForList("SELECT merged_author_id FROM author_merge WHERE author_id = ?",
                Long.class, survivor)).containsExactlyInAnyOrder(shouting, flipped);
            assertThat(credits(misery)).containsExactly(tuple(KING, AUTHOR_ROLE));
            assertThat(credits(joyland)).containsExactly(tuple(KING, AUTHOR_ROLE));
        }

        @Test
        void shouldPreferAuthorWithQid() {
            credit(book(CARRIE), author(KING));
            final long withQid = author(SHOUTING_KING);
            jdbc.update(SET_QID_SQL, withQid);
            credit(book(MISERY), withQid);

            Migrations.migrateTo(POSTGRES, AFTER);

            assertThat(jdbc.queryForList(AUTHOR_IDS_SQL, Long.class)).containsExactly(withQid);
        }

        @Test
        void shouldCreditSurvivorOnceForTwoDuplicates() {
            final long bookId = book("The Hobbit");
            credit(bookId, author(TOLKIEN));
            credit(bookId, author("J. R. R. Tolkien"));

            Migrations.migrateTo(POSTGRES, AFTER);

            assertThat(credits(bookId)).containsExactly(tuple(TOLKIEN, AUTHOR_ROLE));
        }

        @Test
        void shouldCopyQidFromMergedAuthor() {
            final long survivor = author(KING);
            credit(book("It"), survivor);
            credit(book(CARRIE), survivor);
            final long loser = author(SHOUTING_KING);
            jdbc.update(SET_QID_SQL, loser);
            credit(book(MISERY), loser);

            Migrations.migrateTo(POSTGRES, AFTER);

            final String qid =
                jdbc.queryForObject("SELECT wikidata_qid FROM author WHERE author_id = ?", String.class, survivor);
            assertThat(qid).isEqualTo("Q39829");
        }

        @Test
        void shouldTitleCaseShoutingSurvivor() {
            final long shouting = author("TERRY PRATCHETT");
            credit(book("Mort"), shouting);

            Migrations.migrateTo(POSTGRES, AFTER);

            assertThat(nameOf(shouting))
                .isEqualTo("Terry Pratchett");
        }

        @Test
        void shouldCollapseSurvivorSpaces() {
            final long spaced = author("Michael  Kelly");
            credit(book("Northern Nights"), spaced);

            Migrations.migrateTo(POSTGRES, AFTER);

            assertThat(nameOf(spaced))
                .isEqualTo("Michael Kelly");
        }

        @Test
        void shouldStampChangedBooksOnly() {
            final OffsetDateTime seeded = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            final long touched = book(MISERY);
            credit(touched, author(SHOUTING_KING));
            final long untouched = book("Dune");
            credit(untouched, author("Frank Herbert"));
            jdbc.update("UPDATE book SET updated_at = ?", seeded);

            Migrations.migrateTo(POSTGRES, AFTER);

            assertThat(updatedAt(touched)).isAfter(seeded);
            assertThat(updatedAt(untouched)).isEqualTo(seeded);
        }
    }

    private long author(final String name) {
        return jdbc.queryForObject("INSERT INTO author (name) VALUES (?) RETURNING author_id", Long.class, name);
    }

    private String nameOf(final long authorId) {
        return jdbc.queryForObject("SELECT name FROM author WHERE author_id = ?", String.class, authorId);
    }

    private long book(final String title) {
        return jdbc.queryForObject("INSERT INTO book (title, dedup_key) VALUES (?, ?) RETURNING book_id",
            Long.class, title, title);
    }

    private void credit(final long bookId, final long authorId) {
        jdbc.update("INSERT INTO book_author (book_id, author_id) VALUES (?, ?)", bookId, authorId);
    }

    private List<Tuple> credits(final long bookId) {
        return jdbc.query(CREDITS_SQL, (row, index) -> tuple(row.getString(1), row.getString(2)), bookId);
    }

    private OffsetDateTime updatedAt(final long bookId) {
        return jdbc.queryForObject("SELECT updated_at FROM book WHERE book_id = ?", OffsetDateTime.class, bookId)
            .withOffsetSameInstant(ZoneOffset.UTC);
    }
}
