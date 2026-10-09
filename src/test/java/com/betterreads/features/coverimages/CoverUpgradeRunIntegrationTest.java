package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.GOOGLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.HARDCOVER_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static com.betterreads.features.coverimages.CoverImageFixtures.STORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.clients.itunes.ItunesBook;
import com.betterreads.clients.itunes.ItunesUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class CoverUpgradeRunIntegrationTest extends CoverUpgradeJobFixture {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Test
    void shouldStopTheRunAfterAnAppleError() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        save(OTHER_ISBN, GOOGLE_COVER, GOOGLE);
        when(itunes().lookupByIsbn(anyString()))
            .thenThrow(new ItunesUnavailableException())
            .thenReturn(Optional.of(new ItunesBook(APPLE_COVER, STORE)));

        job().upgrade();

        assertThat(List.of(cover(ISBN), cover(OTHER_ISBN)))
            .containsExactly(UNCHANGED_GOOGLE + UNSEARCHED, UNCHANGED_GOOGLE + UNSEARCHED);
    }

    @Test
    void shouldLogTheCountsWhenAppleStopsTheRun(final CapturedOutput output) {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        when(itunes().lookupByIsbn(ISBN)).thenThrow(new ItunesUnavailableException());

        job().upgrade();

        assertThat(output.getOut()).contains("catalog.cover-upgrade checked=0 replaced=0 cleared=0");
    }

    @Test
    void shouldMoveOnAfterABookFails() {
        save(ISBN, GOOGLE_COVER, GOOGLE);
        save(OTHER_ISBN, GOOGLE_COVER, GOOGLE);
        when(itunes().lookupByIsbn(ISBN)).thenThrow(new IllegalStateException("unreadable reply"));

        job().upgrade();

        assertThat(List.of(cover(ISBN), cover(OTHER_ISBN)))
            .containsExactly(UNCHANGED_GOOGLE + SEARCHED, APPLIED_APPLE);
    }

    @Test
    void shouldSkipABookSearchedInsideTheWindow() {
        final long bookId = save(ISBN, GOOGLE_COVER, GOOGLE);
        jdbc().update("UPDATE book SET cover_searched_at = now() WHERE book_id = ?", bookId);

        job().upgrade();

        verify(itunes(), never()).lookupByIsbn(anyString());
    }

    @Test
    void shouldSearchAgainAfterTheWindow() {
        final long bookId = save(ISBN, GOOGLE_COVER, GOOGLE);
        jdbc().update(OLD_SEARCH_SQL, bookId);

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(APPLIED_APPLE);
    }

    @Test
    void shouldSearchAClearedBookAgainAfterTheWindow() {
        final long bookId = save(ISBN, null, null);
        jdbc().update(OLD_SEARCH_SQL, bookId);

        job().upgrade();

        assertThat(cover(ISBN)).isEqualTo(APPLIED_APPLE);
    }

    @Test
    void shouldTakeGoogleCoversFirst() {
        save(ISBN, HARDCOVER_COVER, HARDCOVER);
        final long googleBook = save(OTHER_ISBN, GOOGLE_COVER, null);

        final List<Book> first = books().findUpgradeCandidates(
            OffsetDateTime.now(ZoneOffset.UTC), PageRequest.ofSize(1));

        assertThat(first).extracting(Book::getBookId).containsExactly(googleBook);
    }

    @Test
    void shouldTakeUnsearchedBooksBeforeSearchedOnes() {
        final long searched = save(ISBN, HARDCOVER_COVER, HARDCOVER);
        jdbc().update(OLD_SEARCH_SQL, searched);
        final long unsearched = save(OTHER_ISBN, HARDCOVER_COVER, HARDCOVER);

        final List<Book> first = books().findUpgradeCandidates(
            OffsetDateTime.now(ZoneOffset.UTC), PageRequest.ofSize(1));

        assertThat(first).extracting(Book::getBookId).containsExactly(unsearched);
    }

    @Test
    void shouldCountAReplacedCover(final CapturedOutput output) {
        save(ISBN, GOOGLE_COVER, GOOGLE);

        job().upgrade();

        assertThat(output.getOut()).contains("catalog.cover-upgrade checked=1 replaced=1 cleared=0");
    }

    @Test
    void shouldCountAClearedCover(final CapturedOutput output) {
        wrongCover();

        job().upgrade();

        assertThat(output.getOut()).contains("catalog.cover-upgrade checked=1 replaced=0 cleared=1");
    }

    @Test
    void shouldNotCountAnUnchangedCover(final CapturedOutput output) {
        save(ISBN, APPLE_COVER, GOOGLE);

        job().upgrade();

        assertThat(output.getOut()).contains("catalog.cover-upgrade checked=1 replaced=0 cleared=0");
    }
}
