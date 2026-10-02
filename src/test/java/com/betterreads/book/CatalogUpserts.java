package com.betterreads.book;

import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.googlebooks.GoogleBooksClient;

import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.test.context.TestComponent;

import static org.assertj.core.api.Assertions.assertThat;

@TestComponent
class CatalogUpserts {

    static final String EYE_OF_THE_WORLD_AUTHOR = "Robert Jordan";

    private final BookUpsertService bookUpsertService;

    private final BookRepository bookRepository;

    private final AuthorRepository authorRepository;

    CatalogUpserts(
        final BookUpsertService bookUpsertService,
        final BookRepository bookRepository,
        final AuthorRepository authorRepository) {
        this.bookUpsertService = bookUpsertService;
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    static SourceBook fetchEyeOfTheWorld(final GoogleBooksClient googleBooksClient) {
        final Optional<SourceBook> source = googleBooksClient.fetchByTitleAuthor(
            "The Eye of the World", EYE_OF_THE_WORLD_AUTHOR);
        assertThat(source)
            .as("Google Books returns Eye of the World")
            .isPresent();
        return source.get();
    }

    static void assertIsEyeOfTheWorld(final Book book) {
        assertThat(book.getTitle()).containsIgnoringCase("Eye of the World");
        assertThat(book.getAuthors())
            .as("stored book has Robert Jordan as its sole author")
            .extracting("name")
            .containsExactly(EYE_OF_THE_WORLD_AUTHOR);
    }

    void clear() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    Book upsertAndReloadByVolumeId(final SourceBook source) {
        final Book persisted = bookUpsertService.upsertFromSource(source);

        final String volumeId = Objects.requireNonNull(persisted.getGoogleBooksVolumeId(),
            "upsert left the source volume id unset");
        final Optional<Book> reloaded = bookRepository.findByGoogleBooksVolumeId(volumeId);
        assertThat(reloaded)
            .as("repository finds the persisted volume id")
            .isPresent();
        return reloaded.get();
    }

    Reupsert upsertTwiceKeepingOneBook(final SourceBook source) {
        final Book first = bookUpsertService.upsertFromSource(source);
        final long firstId = first.getBookId();

        final Book second = bookUpsertService.upsertFromSource(source);

        assertThat(second.getBookId())
            .as("second upsert reuses the existing book_id")
            .isEqualTo(firstId);
        assertThat(bookRepository.count())
            .as("book row count remains one after re-upsert")
            .isEqualTo(1L);
        return new Reupsert(first, second);
    }

    record Reupsert(Book first, Book second) {
    }
}
