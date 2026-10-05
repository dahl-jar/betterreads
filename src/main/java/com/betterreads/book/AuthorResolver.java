package com.betterreads.book;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.betterreads.booksource.SourceAuthor;
import com.betterreads.text.AuthorNames;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class AuthorResolver {

    private final AuthorRepository authorRepository;

    AuthorResolver(final AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    List<ResolvedCredit> resolve(final List<SourceAuthor> authors) {
        return authors.stream()
            .flatMap(AuthorResolver::people)
            .map(author -> new ResolvedCredit(findOrCreateAuthor(author), author.role()))
            .toList();
    }

    private static Stream<SourceAuthor> people(final SourceAuthor source) {
        final List<String> people = AuthorNames.split(source.name());
        return people.size() == 1
            ? Stream.of(source)
            : people.stream().map(name -> SourceAuthor.withRole(name, source.role()));
    }

    private Author findOrCreateAuthor(final SourceAuthor source) {
        final Author author = lookup(source).orElseGet(() -> insertOrLookup(source.name()));
        fillMissingFields(author, source);
        return author;
    }

    private Optional<Author> lookup(final SourceAuthor source) {
        final String qid = source.wikidataQid();
        final Optional<Author> byQid = qid == null
            ? Optional.empty()
            : authorRepository.findByWikidataQid(qid);
        return byQid.or(() -> authorRepository.findByNameKey(AuthorNames.key(source.name())));
    }

    private static void fillMissingFields(final Author author, final SourceAuthor source) {
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

    /**
     * Two concurrent upserts can both find the author missing, so the second insert hits the unique
     * {@code author.name_key} constraint and re-reads the existing row.
     */
    private Author insertOrLookup(final String name) {
        final Author created = new Author();
        created.setName(AuthorNames.collapseSpaces(name));
        try {
            return authorRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            return authorRepository.findByNameKey(AuthorNames.key(name))
                .orElseThrow(() -> new IllegalStateException(
                    "author.name_key UNIQUE was violated but no row exists for name=" + name, ex));
        }
    }
}
