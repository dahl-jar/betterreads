package com.betterreads.catalog.repository;

import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Book}. */
public interface BookRepository extends JpaRepository<Book, Long> {

    /** Returns the book with the given Google Books volume id, with authors and subjects fetched. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByGoogleBooksVolumeId(String googleBooksVolumeId);

    /** Returns the book with the given OpenLibrary work key, with authors and subjects fetched. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByOpenLibraryWorkKey(String openLibraryWorkKey);

    /** Returns the book with the given Hardcover id, with authors and subjects fetched. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByHardcoverId(String hardcoverId);

    /** Returns the book with the given LCCN, with authors and subjects fetched. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByLocLccn(String locLccn);
}
