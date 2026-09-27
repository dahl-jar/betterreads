package com.betterreads.book;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT DISTINCT b.seriesName FROM Book b WHERE b.seriesName IS NOT NULL")
    List<String> findDistinctSeriesNames();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.bookId = :bookId")
    Optional<Book> findForUpdate(@Param("bookId") Long bookId);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByDedupKey(String dedupKey);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    List<Book> findAllBy();

    @EntityGraph(attributePaths = "authors")
    List<Book> findByBookIdIn(Collection<Long> bookIds);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByGoogleBooksVolumeId(String googleBooksVolumeId);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByOpenLibraryWorkKey(String openLibraryWorkKey);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByHardcoverId(String hardcoverId);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByLocLccn(String locLccn);

    @EntityGraph(attributePaths = {"authors", "subjects"})
    Optional<Book> findByWikidataQid(String wikidataQid);

    @EntityGraph(attributePaths = "awards")
    Optional<Book> findWithAwardsByWikidataQid(String wikidataQid);
}
