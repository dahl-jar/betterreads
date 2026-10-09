package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.GOOGLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.HARDCOVER_CANDIDATE;
import static com.betterreads.features.coverimages.CoverImageFixtures.HARDCOVER_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.OPEN_LIBRARY_CANDIDATE;
import static com.betterreads.features.coverimages.CoverImageFixtures.OPEN_LIBRARY_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.STORE;
import static com.betterreads.features.coverimages.CoverImageFixtures.wayOfKings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.booksource.CoverSource;
import com.betterreads.clients.hardcoverbook.HardcoverClient;
import com.betterreads.clients.itunes.ItunesApi;
import com.betterreads.clients.itunes.ItunesBook;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class CoverSourcesTest {

    private static final String HARDCOVER_ID = "386446";

    private static final String WORK_KEY = "OL15358691W";

    private final ItunesApi itunes = mock(ItunesApi.class);

    private final HardcoverClient hardcover = mock(HardcoverClient.class);

    private final OpenLibraryClient openLibrary = mock(OpenLibraryClient.class);

    private final CoverSources sources = new CoverSources(itunes, hardcover, openLibrary);

    private final Book book = new Book();

    @BeforeEach
    void setUp() {
        book.applyFrom(wayOfKings(GOOGLE_COVER).hardcoverId(HARDCOVER_ID).openLibraryWorkKey(WORK_KEY).build());
    }

    @Test
    void shouldSkipAppleForABookWithoutAnIsbn() {
        final Book unnumbered = new Book();
        unnumbered.applyFrom(wayOfKings(GOOGLE_COVER).isbn13(null).hardcoverId(HARDCOVER_ID).build());
        when(itunes.lookupByIsbn(any())).thenReturn(Optional.of(new ItunesBook(APPLE_COVER, STORE)));

        final Optional<CoverCandidate> candidate = sources.find(CoverSource.APPLE_BOOKS, unnumbered);

        assertThat(candidate).isEmpty();
    }

    @Test
    void shouldTakeTheHardcoverCoverByHardcoverId() {
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(wayOfKings(HARDCOVER_COVER).build()));

        final Optional<CoverCandidate> candidate = sources.find(CoverSource.HARDCOVER, book);

        assertThat(candidate).contains(HARDCOVER_CANDIDATE);
    }

    @Test
    void shouldSkipABlankHardcoverCover() {
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(wayOfKings(" ").build()));

        final Optional<CoverCandidate> candidate = sources.find(CoverSource.HARDCOVER, book);

        assertThat(candidate).isEmpty();
    }

    @Test
    void shouldSkipHardcoverWhenItFails() {
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenThrow(
            WebClientResponseException.create(HttpStatus.BAD_GATEWAY.value(), "bad gateway", null, null, null));

        final Optional<CoverCandidate> candidate = sources.find(CoverSource.HARDCOVER, book);

        assertThat(candidate).isEmpty();
    }

    @Test
    void shouldSkipHardcoverWhenItsReplyCannotBeRead() {
        when(hardcover.fetchByHardcoverId(HARDCOVER_ID)).thenThrow(new DecodingException("bad body"));

        final Optional<CoverCandidate> candidate = sources.find(CoverSource.HARDCOVER, book);

        assertThat(candidate).isEmpty();
    }

    @Test
    void shouldTakeTheOpenLibraryCoverByWorkKey() {
        when(openLibrary.fetchByWorkKey(WORK_KEY)).thenReturn(Optional.of(wayOfKings(OPEN_LIBRARY_COVER).build()));

        final Optional<CoverCandidate> candidate = sources.find(CoverSource.OPEN_LIBRARY, book);

        assertThat(candidate).contains(OPEN_LIBRARY_CANDIDATE);
    }
}
