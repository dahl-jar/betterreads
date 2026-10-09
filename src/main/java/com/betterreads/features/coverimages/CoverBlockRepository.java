package com.betterreads.features.coverimages;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CoverBlockRepository {

    private static final String BLOCKED_SQL = """
        SELECT EXISTS (
            SELECT 1 FROM cover_blocked
            WHERE regexp_replace(url, '^https://[a-z0-9-]+\\.mzstatic\\.com/', 'https://mzstatic.com/')
                = regexp_replace(?, '^https://[a-z0-9-]+\\.mzstatic\\.com/', 'https://mzstatic.com/'))
        """;

    private final JdbcTemplate jdbc;

    CoverBlockRepository(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean isBlocked(final String url) {
        return Boolean.TRUE.equals(jdbc.queryForObject(BLOCKED_SQL, Boolean.class, url));
    }
}
