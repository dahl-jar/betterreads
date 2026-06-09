package com.betterreads.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Book}. */
public interface BookRepository extends JpaRepository<Book, Long> {

    /** Returns the distinct non-null series names in the catalog, for the daily re-resolve walk. */
    @Query("SELECT DISTINCT b.seriesName FROM Book b WHERE b.seriesName IS NOT NULL")
    List<String> findDistinctSeriesNames();

    /**
     * Returns the book locked for update, so concurrent rating recomputes for the same book run one
     * at a time and each sees the other's committed review.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.bookId = :bookId")
    Optional<Book> findForUpdate(@Param("bookId") Long bookId);

    /**
     * Returns the book with the given dedup key, with authors and subjects fetched.
     *
     * <p>Only authors and subjects are fetched in the graph; awards load lazily within the read
     * transaction.
     */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByDedupKey(String dedupKey);

    /** Returns every book with authors and subjects fetched, for the search reconcile walk. */
    @EntityGraph(attributePaths = {"authors", "subjects"})
    List<Book> findAllBy();

    /** Returns the books with the given ids, authors fetched, for mapping a shelf in one query. */
    @EntityGraph(attributePaths = "authors")
    List<Book> findByBookIdIn(Collection<Long> bookIds);

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
