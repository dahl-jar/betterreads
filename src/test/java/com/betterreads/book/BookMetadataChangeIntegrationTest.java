package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.websearch.MetadataJson;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class BookMetadataChangeIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String TITLE = "Red Rising";

    private static final int STORED_YEAR = 2013;

    private static final int YEAR = 2014;

    private static final int CHECK_VERSION = 2;

    private static final String SOURCE = MetadataJson.SOURCE;

    private static final String CHANGES = " FROM book_metadata_change WHERE book_id = ?";

    private static final String VALUES_SQL = "SELECT field || ': ' || old_value || ' -> ' || new_value" + CHANGES;

    private static final String FIELDS_SQL = "SELECT field" + CHANGES;

    private static final String EVIDENCE_SQL = "SELECT source_url || ', ' || quote || ', ' || check_version" + CHANGES;

    private static final String QUOTE = "Red Rising is a 2014 science fiction novel";

    private static final int MAX_ATTEMPTS = RetrySamples.MAX_ATTEMPTS;

    private static final int RETRY_DAYS = RetrySamples.RETRY_DAYS;

    private static final long UNKNOWN_ID = 999_999L;

    private static final String RETRY_SQL = "SELECT metadata_check_attempts || ' ' || "
        + "(metadata_check_requested_at IS NULL) || ' ' || (metadata_checked_at IS NOT NULL) "
        + "FROM book WHERE book_id = ?";

    @Autowired
    private BookUpsertService upsert;

    @Autowired
    private BookRepository books;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
    }

    @Test
    void shouldRecordTheOldAndNewValueOfAChangedField() {
        final long bookId = save();

        upsert.applyVerified(bookId, verifiedYear(Map.of()), CHECK_VERSION);

        assertThat(jdbc.queryForList(VALUES_SQL, String.class, bookId))
            .containsExactly("YEAR: 2013 -> 2014");
    }

    @Test
    void shouldRecordNothingForAConfirmedUnchangedValue() {
        final long bookId = save();

        upsert.applyVerified(bookId, new VerifiedMetadata(TITLE, null, null, null, null, null, null, null),
            CHECK_VERSION);

        assertThat(jdbc.queryForList(FIELDS_SQL, String.class, bookId)).isEmpty();
    }

    @Test
    void shouldRecordTheSourceAndQuote() {
        final long bookId = save();

        upsert.applyVerified(bookId, verifiedYear(Map.of(VerifiedField.YEAR, new FieldEvidence(SOURCE, QUOTE))),
            CHECK_VERSION);

        assertThat(jdbc.queryForList(EVIDENCE_SQL, String.class, bookId))
            .containsExactly(String.join(", ", SOURCE, QUOTE, String.valueOf(CHECK_VERSION)));
    }

    @Test
    void shouldGiveUpAfterTheThirdDeferral() {
        final long bookId = save();
        final OffsetDateTime retryAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(RETRY_DAYS);
        upsert.deferMetadataCheck(bookId, retryAt, MAX_ATTEMPTS);
        upsert.deferMetadataCheck(bookId, retryAt, MAX_ATTEMPTS);

        final boolean retrying = upsert.deferMetadataCheck(bookId, retryAt, MAX_ATTEMPTS);

        assertThat(retrying).isFalse();
        assertThat(jdbc.queryForObject(RETRY_SQL, String.class, bookId)).isEqualTo("3 true true");
    }

    @Test
    void shouldRefuseToDeferAnUnknownBook() {
        final OffsetDateTime retryAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(RETRY_DAYS);

        assertThatThrownBy(() -> upsert.deferMetadataCheck(UNKNOWN_ID, retryAt, MAX_ATTEMPTS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static VerifiedMetadata verifiedYear(final Map<VerifiedField, FieldEvidence> evidence) {
        return new VerifiedMetadata(null, null, YEAR, null, null, null, null, null, false, false, evidence);
    }

    private long save() {
        final SourceBook source = SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .openLibraryWorkKey("OL1W")
            .title(TITLE)
            .publicationYear(STORED_YEAR)
            .build();
        return upsert.upsertFromSource(source).getBookId();
    }
}
