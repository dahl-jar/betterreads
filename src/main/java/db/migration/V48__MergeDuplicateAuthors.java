package db.migration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.betterreads.text.AuthorNames;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// checkstyle:TypeName, PMD.ClassNamingConventions: Flyway reads the version from the V48__ class name.
@SuppressWarnings({"checkstyle:TypeName", "PMD.ClassNamingConventions"})
public class V48__MergeDuplicateAuthors extends BaseJavaMigration {

    private static final String AUTHOR_ID = "author_id";

    private static final String RANK_POSITIONS_SQL = """
        UPDATE book_author
        SET position = ranked.position
        FROM (
            SELECT book_id, author_id, row_number() OVER (PARTITION BY book_id ORDER BY author_id) - 1 AS position
            FROM book_author
        ) AS ranked
        WHERE book_author.book_id = ranked.book_id
            AND book_author.author_id = ranked.author_id
        """;

    private static final String AUTHORS_SQL = "SELECT author_id, name, wikidata_qid, photo_url, bio FROM author";

    private static final String CREDITS_SQL = "SELECT book_id, author_id FROM book_author ORDER BY book_id, author_id";

    private static final String INSERT_CREDIT_SQL =
        "INSERT INTO book_author (book_id, author_id, position) VALUES (?, ?, ?)";

    private static final String UPDATE_AUTHOR_SQL = """
        UPDATE author
        SET name = ?, wikidata_qid = ?, photo_url = ?, bio = ?, updated_at = now()
        WHERE author_id = ?
        """;

    @Override
    public void migrate(final Context context) {
        final JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        jdbc.update(RANK_POSITIONS_SQL);
        final Map<Long, Row> authors = jdbc.query(AUTHORS_SQL, (rs, index) -> new Row(
                rs.getLong(AUTHOR_ID), rs.getString("name"), rs.getString("wikidata_qid"),
                rs.getString("photo_url"), rs.getString("bio")))
            .stream()
            .collect(Collectors.toMap(Row::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        final Map<Long, List<Long>> books = new LinkedHashMap<>();
        jdbc.query(CREDITS_SQL, rs -> {
            books.computeIfAbsent(rs.getLong("book_id"), id -> new ArrayList<>()).add(rs.getLong(AUTHOR_ID));
        });
        new Merge(jdbc, authors, books).run();
    }

    private record Row(long id, String name, @Nullable String qid, @Nullable String photo, @Nullable String bio) {

        String cleanName() {
            return AuthorNames.isSingleCase(name) ? AuthorNames.display(name) : AuthorNames.collapseSpaces(name);
        }
    }

    private static final class Merge {

        private final JdbcTemplate jdbc;

        private final Map<Long, Row> authors;

        private final Map<Long, List<Long>> books;

        private final Map<Long, Long> survivorOf = new HashMap<>();

        private final Set<Long> touched = new HashSet<>();

        Merge(final JdbcTemplate jdbc, final Map<Long, Row> authors, final Map<Long, List<Long>> books) {
            this.jdbc = jdbc;
            this.authors = authors;
            this.books = books;
        }

        void run() {
            pickSurvivors();
            books.replaceAll(this::repointed);
            markRenamedBooks();
            rewriteTouchedCredits();
            final Set<Long> merged = merged();
            recordMerges(merged);
            jdbc.update("DELETE FROM author WHERE author_id = ANY (?)", (Object) merged.toArray(Long[]::new));
            updateSurvivors();
            jdbc.batchUpdate("UPDATE author SET name_key = ? WHERE author_id = ?", authors.values().stream()
                .filter(author -> !merged.contains(author.id()))
                .map(author -> new Object[] {AuthorNames.key(author.cleanName()), author.id()})
                .toList());
            jdbc.update("UPDATE book SET updated_at = now() WHERE book_id = ANY (?)",
                (Object) touched.toArray(Long[]::new));
            jdbc.execute("ALTER TABLE author ADD CONSTRAINT author_name_key_key UNIQUE (name_key)");
            jdbc.execute("ALTER TABLE author ALTER COLUMN name_key SET NOT NULL");
        }

        private void pickSurvivors() {
            final Map<Long, Long> creditCounts = books.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            final Comparator<Row> preferred = Comparator
                .<Row>comparingLong(author -> creditCounts.getOrDefault(author.id(), 0L)).reversed()
                .thenComparing(author -> author.qid() == null)
                .thenComparingLong(Row::id);
            authors.values().stream()
                .collect(Collectors.groupingBy(author -> AuthorNames.key(author.name())))
                .values()
                .forEach(group -> {
                    final List<Row> ranked = group.stream().sorted(preferred).toList();
                    ranked.forEach(author -> survivorOf.put(author.id(), ranked.getFirst().id()));
                });
        }

        private List<Long> repointed(final long bookId, final List<Long> credits) {
            final List<Long> result = credits.stream().map(survivorOf::get).distinct().toList();
            if (!result.equals(credits)) {
                touched.add(bookId);
            }
            return result;
        }

        private void markRenamedBooks() {
            final Set<Long> renamed = authors.values().stream()
                .filter(author -> !author.cleanName().equals(author.name()))
                .map(Row::id)
                .collect(Collectors.toSet());
            books.forEach((bookId, credits) -> {
                if (credits.stream().anyMatch(renamed::contains)) {
                    touched.add(bookId);
                }
            });
        }

        private void rewriteTouchedCredits() {
            jdbc.update("DELETE FROM book_author WHERE book_id = ANY (?)", (Object) touched.toArray(Long[]::new));
            jdbc.batchUpdate(INSERT_CREDIT_SQL, touched.stream()
                .flatMap(bookId -> {
                    final List<Long> credits = books.get(bookId);
                    return IntStream.range(0, credits.size())
                        .mapToObj(position -> new Object[] {bookId, credits.get(position), position});
                })
                .toList());
        }

        private Set<Long> merged() {
            return survivorOf.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        }

        private void recordMerges(final Set<Long> merged) {
            jdbc.batchUpdate("INSERT INTO author_merge (merged_author_id, author_id, merged_name) VALUES (?, ?, ?)",
                merged.stream()
                    .map(authorId -> new Object[] {authorId, survivorOf.get(authorId), authors.get(authorId).name()})
                    .toList());
        }

        private void updateSurvivors() {
            final Map<Long, List<Row>> groups = authors.values().stream()
                .collect(Collectors.groupingBy(author -> survivorOf.get(author.id())));
            jdbc.batchUpdate(UPDATE_AUTHOR_SQL, groups.entrySet().stream()
                .filter(group -> group.getValue().size() > 1
                    || !authors.get(group.getKey()).cleanName().equals(authors.get(group.getKey()).name()))
                .map(group -> {
                    final Row survivor = authors.get(group.getKey());
                    final List<Row> members = group.getValue();
                    return new Object[] {
                        survivor.cleanName(),
                        first(survivor.qid(), members, Row::qid),
                        first(survivor.photo(), members, Row::photo),
                        first(survivor.bio(), members, Row::bio),
                        survivor.id()};
                })
                .toList());
        }

        private static @Nullable String first(
            final @Nullable String own, final List<Row> members, final Function<Row, @Nullable String> field) {
            return own != null ? own : members.stream()
                .map(field)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        }
    }
}
