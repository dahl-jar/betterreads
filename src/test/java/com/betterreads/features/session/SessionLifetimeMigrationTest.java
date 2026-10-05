package com.betterreads.features.session;

import static com.betterreads.testsupport.Accounts.OTHER_USER;
import static com.betterreads.testsupport.Accounts.THIRD_USER;
import static com.betterreads.testsupport.Accounts.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.betterreads.testsupport.Migrations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SessionLifetimeMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String BEFORE = "44";

    private static final String AFTER = "45";

    private static final int LIFETIME_DAYS = 30;

    private static final int RECENT_LOGIN_DAYS = 10;

    private static final int OLD_LOGIN_DAYS = 40;

    private static final String ADD_USER_SQL = """
        INSERT INTO app_user (username, email, password_hash, email_verified_at)
        VALUES (?, ?, repeat('x', 60), CASE WHEN ? THEN now() END)
        RETURNING user_id
        """;

    private static final String ADD_ROTATED_SESSION_SQL = """
        WITH active AS (
            INSERT INTO refresh_token (user_id, token_hash, issued_at, expires_at)
            VALUES (?, 'active-' || ?, now() - interval '1 day', now() + interval '29 days')
            RETURNING refresh_token_id
        )
        INSERT INTO refresh_token (user_id, token_hash, issued_at, expires_at, revoked_at, replaced_by)
        SELECT ?, 'login-' || ?, now() - make_interval(days => ?), now() + interval '1 day',
            now() - interval '1 day', refresh_token_id
        FROM active
        """;

    private static final String REVOKED_SQL =
        "SELECT revoked_at IS NOT NULL FROM refresh_token WHERE token_hash = 'active-' || ?";

    private static final String PERSISTENT_SQL =
        "SELECT persistent FROM refresh_token WHERE token_hash = 'active-' || ?";

    private static final String EXPIRY_SQL = "SELECT expires_at FROM refresh_token WHERE token_hash = 'active-' || ?";

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToTheVersionBefore() {
        jdbc = Migrations.resetTo(POSTGRES, BEFORE);
    }

    @Test
    void shouldEndASessionThirtyDaysAfterItsLogin() {
        seedRotatedSession(USER, true, RECENT_LOGIN_DAYS);

        Migrations.migrateTo(POSTGRES, AFTER);

        final OffsetDateTime expiry = jdbc.queryForObject(EXPIRY_SQL, OffsetDateTime.class, USER);
        final OffsetDateTime expected = OffsetDateTime.now(ZoneOffset.UTC).plusDays(LIFETIME_DAYS - RECENT_LOGIN_DAYS);
        assertThat(isRevoked(USER)).isFalse();
        assertThat(expiry).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
    }

    @Test
    void shouldKeepAnExistingSessionRemembered() {
        seedRotatedSession(USER, true, RECENT_LOGIN_DAYS);

        Migrations.migrateTo(POSTGRES, AFTER);

        assertThat(jdbc.queryForObject(PERSISTENT_SQL, Boolean.class, USER)).isTrue();
    }

    @Test
    void shouldRevokeASessionWhoseLoginIsThirtyDaysOld() {
        seedRotatedSession(THIRD_USER, true, OLD_LOGIN_DAYS);

        Migrations.migrateTo(POSTGRES, AFTER);

        assertThat(isRevoked(THIRD_USER)).isTrue();
    }

    @Test
    void shouldRevokeTheSessionOfAnUnverifiedAccount() {
        seedRotatedSession(OTHER_USER, false, RECENT_LOGIN_DAYS);

        Migrations.migrateTo(POSTGRES, AFTER);

        assertThat(isRevoked(OTHER_USER)).isTrue();
    }

    private void seedRotatedSession(final String username, final boolean verified, final int loginDaysAgo) {
        final Long userId = jdbc.queryForObject(
            ADD_USER_SQL, Long.class, username, username + "@example.com", verified);
        jdbc.update(ADD_ROTATED_SESSION_SQL, userId, username, userId, username, loginDaysAgo);
    }

    private boolean isRevoked(final String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject(REVOKED_SQL, Boolean.class, username));
    }
}
