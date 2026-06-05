package com.betterreads.catalog.repository;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Book}. */
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Returns the book with the given dedup key, with authors and subjects fetched.
     *
     * <p>Only authors and subjects are fetched in the graph: Hibernate cannot join-fetch two list
     * collections at once, so awards load lazily within the read transaction.
     */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByDedupKey(String dedupKey);

    /** Returns every book with authors and subjects fetched, for the search reconcile walk. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    List<Book> findAllBy();

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

    /** Returns the book with the given Wikidata QID, with authors and subjects fetched. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByWikidataQid(String wikidataQid);

    /** Returns the book with the given Wikidata QID, with its awards fetched. */
    @EntityGraph(attributePaths = "awards")
    Optional<Book> findWithAwardsByWikidataQid(String wikidataQid);
}
