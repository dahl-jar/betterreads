package com.betterreads.catalog.repository;

import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Book}. */
public interface BookRepository extends JpaRepository<Book, Long> {

    /** Returns the book with the given Google Books volume id, with its {@code authors} fetched. */
    @EntityGraph(attributePaths = "authors")
    Optional<Book> findByGoogleBooksVolumeId(String googleBooksVolumeId);
}
