package com.betterreads.catalog.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.repository.AuthorRepository;
import com.betterreads.catalog.repository.BookRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists external metadata into the local catalog. Identity per source comes from the
 * source-specific id column on {@code book}, so the upsert is a lookup-then-save in a single
 * transaction.
 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private final BookRepository bookRepository;

    private final AuthorRepository authorRepository;

    public CatalogServiceImpl(
        final BookRepository bookRepository,
        final AuthorRepository authorRepository
    ) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    @Override
    @Transactional
    public Book upsertFromSource(final SourceBook source) {
        final Book book = findExistingForSource(source).orElseGet(Book::new);
        book.applyFrom(source);
        attachAuthors(book, source.authors());
        return bookRepository.save(book);
    }

    private Optional<Book> findExistingForSource(final SourceBook source) {
        return identityLookups().stream()
            .map(lookup -> lookup.find(source))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private List<SourceIdentityLookup> identityLookups() {
        return List.of(
            new SourceIdentityLookup(
                SourceBook::googleBooksVolumeId, bookRepository::findByGoogleBooksVolumeId),
            new SourceIdentityLookup(
                SourceBook::openLibraryWorkKey, bookRepository::findByOpenLibraryWorkKey),
            new SourceIdentityLookup(
                SourceBook::hardcoverId, bookRepository::findByHardcoverId),
            new SourceIdentityLookup(
                SourceBook::locLccn, bookRepository::findByLocLccn),
            new SourceIdentityLookup(
                SourceBook::wikidataQid, bookRepository::findByWikidataQid));
    }

    private record SourceIdentityLookup(
        Function<SourceBook, @Nullable String> idOf,
        Function<String, Optional<Book>> findById
    ) {

        Optional<Book> find(final SourceBook source) {
            final String sourceId = idOf.apply(source);
            return sourceId == null ? Optional.empty() : findById.apply(sourceId);
        }
    }

    private void attachAuthors(final Book book, final @Nullable List<SourceAuthor> authors) {
        if (authors == null) {
            return;
        }
        authors.stream()
            .filter(author -> !author.name().isBlank())
            .map(this::findOrCreateAuthor)
            .forEach(author -> book.getAuthors().add(author));
    }

    /**
     * Returns the author matching the source author, by Wikidata QID first and then by name,
     * creating one when neither matches. Wikidata's photo and bio fill the row without overwriting a
     * set value with null.
     *
     * <p>The {@code DataIntegrityViolationException} path handles the race between two concurrent
     * upserts both finding the author missing and both trying to insert. The {@code UNIQUE}
     * constraint on {@code author.name} (V21) lets the losing transaction re-query under the existing
     * row instead of writing a duplicate.
     */
    private Author findOrCreateAuthor(final SourceAuthor source) {
        final Author author = lookup(source).orElseGet(() -> insertOrLookup(source.name()));
        fillIdentityFields(author, source);
        return author;
    }

    private Optional<Author> lookup(final SourceAuthor source) {
        final String qid = source.wikidataQid();
        final Optional<Author> byQid = qid == null
            ? Optional.empty()
            : authorRepository.findByWikidataQid(qid);
        return byQid.or(() -> authorRepository.findByName(source.name()));
    }

    private static void fillIdentityFields(final Author author, final SourceAuthor source) {
        if (author.getWikidataQid() == null) {
            author.setWikidataQid(source.wikidataQid());
        }
        if (author.getPhotoUrl() == null) {
            author.setPhotoUrl(source.photoUrl());
        }
        if (author.getBio() == null) {
            author.setBio(source.bio());
        }
    }

    private Author insertOrLookup(final String name) {
        final Author created = new Author();
        created.setName(name);
        try {
            return authorRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            return authorRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                    "author.name UNIQUE was violated but no row exists for name=" + name, ex));
        }
    }
}
