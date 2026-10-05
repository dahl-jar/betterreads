package com.betterreads.testsupport;

import com.betterreads.text.AuthorNames;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CatalogRows {

    private CatalogRows() {
    }

    public static long author(final JdbcTemplate jdbc, final String name) {
        return jdbc.queryForObject("INSERT INTO author (name, name_key) VALUES (?, ?) RETURNING author_id",
            Long.class, name, AuthorNames.key(name));
    }
}
