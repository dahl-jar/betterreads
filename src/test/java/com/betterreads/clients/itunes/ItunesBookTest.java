package com.betterreads.clients.itunes;

import static com.betterreads.clients.itunes.ItunesSearchJson.ARTWORK;
import static com.betterreads.clients.itunes.ItunesSearchJson.STORE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ItunesBookTest {

    @Test
    void shouldRejectArtworkFromALookAlikeHost() {
        final Optional<ItunesBook> book = ItunesBook.of("https://evilmzstatic.com/3000x3000bb.jpg", STORE);

        assertThat(book).isEmpty();
    }

    @Test
    void shouldRejectAStoreLinkFromALookAlikeHost() {
        final Optional<ItunesBook> book = ItunesBook.of(ARTWORK, "https://books.apple.com.example.test/us/book/id1");

        assertThat(book).isEmpty();
    }

    @Test
    void shouldRejectAPlainHttpStoreLink() {
        final Optional<ItunesBook> book = ItunesBook.of(ARTWORK, "http://books.apple.com/us/book/id1");

        assertThat(book).isEmpty();
    }

    @Test
    void shouldRejectArtworkWithoutAHost() {
        final Optional<ItunesBook> book = ItunesBook.of("https:///3000x3000bb.jpg", STORE);

        assertThat(book).isEmpty();
    }

    @Test
    void shouldRejectAMalformedStoreLink() {
        final Optional<ItunesBook> book = ItunesBook.of(ARTWORK, "https://books.apple.com/us/book/a b");

        assertThat(book).isEmpty();
    }

    @Test
    void shouldAcceptArtworkFromAnAppleImageServer() {
        final Optional<ItunesBook> book = ItunesBook.of(ARTWORK, STORE);

        assertThat(book).contains(new ItunesBook(ARTWORK, STORE));
    }
}
