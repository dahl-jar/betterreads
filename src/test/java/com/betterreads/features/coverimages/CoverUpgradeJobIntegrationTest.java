package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.GOOGLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static com.betterreads.features.coverimages.CoverImageFixtures.STORE;
import static com.betterreads.features.coverimages.CoverImageFixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.book.BookChangedEvent;
import com.betterreads.clients.itunes.ItunesBook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@RecordApplicationEvents
class CoverUpgradeJobIntegrationTest extends CoverUpgradeJobFixture {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String NEW_APPLE_COVER = "https://is1-ssl.mzstatic.com/b/3000x3000bb.jpg";

    private static final String OTHER_SERVER_APPLE_COVER = "https://is5-ssl.mzstatic.com/a/3000x3000bb.jpg";

    @Autowired
    private ApplicationEvents events;

    @Test
    void shouldReplaceAGoogleCover() {
        save(ISBN, GOOGLE_COVER, GOOGLE);

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(APPLIED_APPLE);
    }

    @Test
    void shouldAnnounceTheChangedBook() {
        final long bookId = save(ISBN, GOOGLE_COVER, GOOGLE);

        job().upgrade();

        assertThat(events.stream(BookChangedEvent.class).map(BookChangedEvent::bookId)).containsExactly(bookId);
    }

    @Test
    void shouldKeepASmallCoverWhenNoCandidatePasses() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        when(itunes().lookupByIsbn(ISBN)).thenReturn(Optional.empty());

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(UNCHANGED_GOOGLE + SEARCHED);
    }

    @Test
    void shouldClearAWrongCoverWhenNoCandidatePasses() {
        wrongCover();

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(CLEARED);
    }

    @Test
    void shouldLeaveTheBookUnsearchedWhenTheMirrorFails() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        when(mirror().mirror(anyString(), anyString(), any())).thenReturn(Optional.empty());

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(UNCHANGED_GOOGLE + UNSEARCHED);
    }

    @Test
    void shouldLeaveASearchedAppleCoverAlone() {
        final long bookId = save(ISBN, APPLE_COVER, APPLE);
        jdbc().update(OLD_SEARCH_SQL, bookId);
        offerNewApple();

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(APPLE_COVER + " " + APPLE + " none none " + SEARCHED);
    }

    @Test
    void shouldAnnounceAClearedBook() {
        final long bookId = wrongCover();

        job().upgrade();

        assertThat(events.stream(BookChangedEvent.class).map(BookChangedEvent::bookId)).containsExactly(bookId);
    }

    @Test
    void shouldSkipABlockedAppleCoverOnAnotherImageServer() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        jdbc().update(BLOCK_SQL, APPLE_COVER);
        when(itunes().lookupByIsbn(ISBN)).thenReturn(Optional.of(new ItunesBook(OTHER_SERVER_APPLE_COVER, STORE)));
        when(check().passes(bytes(OTHER_SERVER_APPLE_COVER))).thenReturn(true);

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(UNCHANGED_GOOGLE + SEARCHED);
    }

    @Test
    void shouldTakeANewAppleImageWhenAnOlderOneIsBlocked() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        jdbc().update(BLOCK_SQL, APPLE_COVER);
        offerNewApple();

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(applied(NEW_APPLE_COVER));
    }

    @Test
    void shouldTakeDownABlockedAppleCover() {
        save(ISBN, APPLE_COVER, APPLE);
        jdbc().update(BLOCK_SQL, APPLE_COVER);

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(CLEARED);
    }

    private void offerNewApple() {
        when(itunes().lookupByIsbn(ISBN)).thenReturn(Optional.of(new ItunesBook(NEW_APPLE_COVER, STORE)));
        when(check().passes(bytes(NEW_APPLE_COVER))).thenReturn(true);
    }
}
