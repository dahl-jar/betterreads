package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.GOOGLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.betterreads.clients.itunes.ItunesUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class CoverUpgradeStallIntegrationTest extends CoverUpgradeJobFixture {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String STILL_OLD_SQL =
        "SELECT cover_searched_at < now() - interval '59 days' FROM book WHERE book_id = ?";

    @Test
    void shouldKeepTheOldSearchTimeWhenAppleStopsTheRun() {
        final long bookId = save(ISBN, GOOGLE_COVER, GOOGLE);
        jdbc().update(OLD_SEARCH_SQL, bookId);
        when(itunes().lookupByIsbn(ISBN)).thenThrow(new ItunesUnavailableException());

        job().upgrade();

        final Boolean stillOld = jdbc().queryForObject(STILL_OLD_SQL, Boolean.class, bookId);
        assertThat(stillOld).isTrue();
    }

    @Test
    void shouldMoveOnAfterABookKillsTheRun() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        save(OTHER_ISBN, GOOGLE_COVER, GOOGLE);
        when(itunes().lookupByIsbn(ISBN)).thenThrow(new OutOfMemoryError());
        assertThatThrownBy(job()::upgrade).isInstanceOf(OutOfMemoryError.class);

        final Throwable next = catchThrowable(job()::upgrade);

        assertThat(next).isNull();
        assertThat(cover(ISBN)).isEqualTo(UNCHANGED_GOOGLE + SEARCHED);
        assertThat(cover(OTHER_ISBN)).isEqualTo(APPLIED_APPLE);
    }
}
