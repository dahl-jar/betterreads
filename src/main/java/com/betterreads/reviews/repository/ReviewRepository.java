package com.betterreads.reviews.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.betterreads.reviews.entity.Review;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Review}, keyed per user and book. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndBookId(Long userId, Long bookId);

    /**
     * Returns the review locked for update, so a comment posted on it commits or fails before a
     * concurrent delete of the review, never leaving an orphan comment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.reviewId = :reviewId")
    Optional<Review> findForUpdate(@Param("reviewId") Long reviewId);

    /** A book's reviews, newest edit first, with a stable id tiebreak for paging. */
    @Query("SELECT r FROM Review r WHERE r.bookId = :bookId "
        + "ORDER BY r.updatedAt DESC, r.reviewId DESC")
    Page<Review> findForBook(@Param("bookId") Long bookId, Pageable pageable);

    /** The user's reviews, newest edit first, with a stable id tiebreak for paging. */
    @Query("SELECT r FROM Review r WHERE r.userId = :userId "
        + "ORDER BY r.updatedAt DESC, r.reviewId DESC")
    Page<Review> findForUser(@Param("userId") Long userId, Pageable pageable);

    long deleteByUserIdAndBookId(Long userId, Long bookId);

    /** Returns the user's reviews among the given books that carry a rating; prose-only reviews are excluded. */
    @Query("""
        SELECT r FROM Review r
        WHERE r.userId = :userId AND r.bookId IN :bookIds AND r.rating IS NOT NULL
        """)
    List<Review> findRatedByUserForBooks(
        @Param("userId") Long userId, @Param("bookIds") Collection<Long> bookIds);

    /**
     * Returns the average rating and rating count for a book over its rated reviews.
     *
     * <p>Reviews with a null rating (prose-only) are excluded from both, so the count matches the
     * average's denominator. A book with no rated reviews returns a zero count and a null average.
     */
    @Query("""
        SELECT new com.betterreads.reviews.repository.RatingAggregate(
            AVG(r.rating), COUNT(r.rating))
        FROM Review r
        WHERE r.bookId = :bookId AND r.rating IS NOT NULL
        """)
    RatingAggregate aggregateRatingForBook(@Param("bookId") Long bookId);

    /**
     * Returns the review count at each star rating present for a book.
     *
     * <p>Only stars with at least one review appear; a star with no reviews is absent from the
     * result, not a zero row, so the caller zero-fills the missing stars.
     */
    @Query("""
        SELECT new com.betterreads.reviews.repository.RatingBucket(r.rating, COUNT(r))
        FROM Review r
        WHERE r.bookId = :bookId AND r.rating IS NOT NULL
        GROUP BY r.rating
        """)
    List<RatingBucket> countByStarForBook(@Param("bookId") Long bookId);
}
