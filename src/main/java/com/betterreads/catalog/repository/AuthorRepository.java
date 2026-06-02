package com.betterreads.catalog.repository;

import java.util.Optional;

import com.betterreads.catalog.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Author}. */
public interface AuthorRepository extends JpaRepository<Author, Long> {

    /** Returns the author with the given display name, case-sensitive. */
    Optional<Author> findByName(String name);

    /** Returns the author with the given Wikidata QID, or empty if none matches. */
    Optional<Author> findByWikidataQid(String wikidataQid);
}
