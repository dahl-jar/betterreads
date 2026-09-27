package com.betterreads.features.reviews;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndBookId(Long userId, Long bookId);

    /**
     * a comment posted during a concurrent review delete would be orphaned, so the comment write
     * locks the review first
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.reviewId = :reviewId")
    Optional<Review> findForUpdate(@Param("reviewId") Long reviewId);

    /** reviews edited in the same instant would swap between pages, so the review id breaks the tie */
    @Query("SELECT r FROM Review r WHERE r.bookId = :bookId "
        + "ORDER BY r.updatedAt DESC, r.reviewId DESC")
    Page<Review> findForBook(@Param("bookId") Long bookId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.userId = :userId "
        + "ORDER BY r.updatedAt DESC, r.reviewId DESC")
    Page<Review> findForUser(@Param("userId") Long userId, Pageable pageable);

    long deleteByUserIdAndBookId(Long userId, Long bookId);

    @Query("""
        SELECT r FROM Review r
        WHERE r.userId = :userId AND r.bookId IN :bookIds AND r.rating IS NOT NULL
        """)
    List<Review> findRatedByUserForBooks(
        @Param("userId") Long userId, @Param("bookIds") Collection<Long> bookIds);

    @Query("""
        SELECT new com.betterreads.features.reviews.ReviewRatingAggregate(
            AVG(r.rating), COUNT(r.rating))
        FROM Review r
        WHERE r.bookId = :bookId AND r.rating IS NOT NULL
        """)
    ReviewRatingAggregate aggregateRatingForBook(@Param("bookId") Long bookId);

    /** stars nobody picked have no row */
    @Query("""
        SELECT new com.betterreads.features.reviews.StarCount(r.rating, COUNT(r))
        FROM Review r
        WHERE r.bookId = :bookId AND r.rating IS NOT NULL
        GROUP BY r.rating
        """)
    List<StarCount> countByStarForBook(@Param("bookId") Long bookId);
}
