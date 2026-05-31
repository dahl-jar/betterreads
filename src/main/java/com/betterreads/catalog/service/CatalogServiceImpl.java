package com.betterreads.catalog.service;

import java.util.List;
import java.util.Optional;

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
        attachAuthors(book, source.authorNames());
        return bookRepository.save(book);
    }

    private Optional<Book> findExistingForSource(final SourceBook source) {
        if (source.googleBooksVolumeId() != null) {
            return bookRepository.findByGoogleBooksVolumeId(source.googleBooksVolumeId());
        }
        if (source.openLibraryWorkKey() != null) {
            return bookRepository.findByOpenLibraryWorkKey(source.openLibraryWorkKey());
        }
        if (source.hardcoverId() != null) {
            return bookRepository.findByHardcoverId(source.hardcoverId());
        }
        return Optional.empty();
    }

    private void attachAuthors(final Book book, final @Nullable List<String> authorNames) {
        if (authorNames == null) {
            return;
        }
        authorNames.stream()
            .filter(name -> name != null && !name.isBlank())
            .map(this::findOrCreateAuthor)
            .forEach(author -> book.getAuthors().add(author));
    }

    /**
     * Returns an existing author with this name or creates one.
     *
     * <p>The {@code DataIntegrityViolationException} path handles the race between two
     * concurrent upserts both finding the author missing and both trying to insert. The
     * {@code UNIQUE} constraint on {@code author.name} (V21) lets the losing transaction
     * re-query under the existing row instead of writing a duplicate.
     */
    private Author findOrCreateAuthor(final String name) {
        return authorRepository.findByName(name).orElseGet(() -> insertOrLookup(name));
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
