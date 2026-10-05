package com.betterreads.testsupport;

import java.util.Map;
import java.util.Properties;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class Migrations {

    private static final String UTC_SESSION = "SET TIME ZONE 'UTC'";

    private Migrations() {
    }

    public static JdbcTemplate resetTo(final PostgreSQLContainer postgres, final String version) {
        flyway(postgres, version).clean();
        migrateTo(postgres, version);
        final DriverManagerDataSource dataSource =
            new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final Properties utc = new Properties();
        utc.setProperty("options", "-c TimeZone=UTC");
        dataSource.setConnectionProperties(utc);
        return new JdbcTemplate(dataSource);
    }

    public static void migrateTo(final PostgreSQLContainer postgres, final String version) {
        flyway(postgres, version).migrate();
    }

    private static Flyway flyway(final PostgreSQLContainer postgres, final String version) {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .placeholders(Map.of("appPassword", "app-password"))
            .initSql(UTC_SESSION)
            .cleanDisabled(false)
            .target(version)
            .load();
    }
}
