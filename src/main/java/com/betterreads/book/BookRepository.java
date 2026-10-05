package com.betterreads.book;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM book WHERE hardcover_id = :hardcoverId AND verified_fields LIKE '%SERIES%')
        """, nativeQuery = true)
    boolean existsSeriesVerifiedByHardcoverId(@Param("hardcoverId") String hardcoverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.bookId = :bookId")
    Optional<Book> findForUpdate(@Param("bookId") Long bookId);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects"})
    Optional<Book> findByDedupKey(String dedupKey);

    @Query("""
        SELECT b.bookId FROM Book b
        WHERE b.updatedAt >= :since AND b.bookId > :afterId
        ORDER BY b.bookId
        """)
    List<Long> findIdsChangedSince(
        @Param("since") OffsetDateTime since, @Param("afterId") long afterId, Pageable pageable);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects", "series"})
    List<Book> findWithSubjectsByBookIdIn(Collection<Long> bookIds);

    @EntityGraph(attributePaths = {"credits", "credits.author"})
    List<Book> findByBookIdIn(Collection<Long> bookIds);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects"})
    Optional<Book> findByGoogleBooksVolumeId(String googleBooksVolumeId);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects"})
    Optional<Book> findByOpenLibraryWorkKey(String openLibraryWorkKey);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects"})
    Optional<Book> findByHardcoverId(String hardcoverId);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects"})
    Optional<Book> findByLocLccn(String locLccn);

    @EntityGraph(attributePaths = {"credits", "credits.author", "subjects"})
    Optional<Book> findByWikidataQid(String wikidataQid);

    @EntityGraph(attributePaths = "awards")
    Optional<Book> findWithAwardsByWikidataQid(String wikidataQid);
}
