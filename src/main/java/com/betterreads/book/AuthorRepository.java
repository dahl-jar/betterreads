package com.betterreads.book;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    /** Matches the name case-sensitively. */
    Optional<Author> findByName(String name);

    Optional<Author> findByWikidataQid(String wikidataQid);
}
