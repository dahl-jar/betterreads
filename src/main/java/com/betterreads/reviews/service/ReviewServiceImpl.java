package com.betterreads.reviews.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.betterreads.catalog.dto.BookCommunityRating;
import com.betterreads.catalog.dto.BookSummary;
import com.betterreads.catalog.service.read.BookCommunityRatingReader;
import com.betterreads.catalog.service.read.BookIdLookup;
import com.betterreads.catalog.service.read.BookSummaryReader;
import com.betterreads.common.dto.PageQuery;
import com.betterreads.common.exception.ResourceNotFoundException;
import com.betterreads.common.util.ConflictRetry;
import com.betterreads.reviews.dto.CommunityRatingResponse;
import com.betterreads.reviews.dto.ReviewPage;
import com.betterreads.reviews.dto.ReviewResponse;
import com.betterreads.reviews.dto.StarCount;
import com.betterreads.reviews.dto.UpsertReviewRequest;
import com.betterreads.reviews.entity.Review;
import com.betterreads.reviews.mapper.ReviewMapper;
import com.betterreads.reviews.repository.RatingBucket;
import com.betterreads.reviews.repository.ReviewRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link ReviewService}, backed by {@link ReviewRepository}. */
@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private static final int MAX_UPSERT_ATTEMPTS = 3;

    private static final int HIGHEST_STAR = 5;

    private static final int LOWEST_STAR = 1;

    private final ReviewRepository reviews;

    private final BookIdLookup bookIds;

    private final BookSummaryReader bookSummaries;

    private final BookCommunityRatingReader communityRatings;

    private final ReviewMapper mapper;

    private final ReviewWriter writer;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ReviewServiceImpl(
        final ReviewRepository reviews,
        final BookIdLookup bookIds,
        final BookSummaryReader bookSummaries,
        final BookCommunityRatingReader communityRatings,
        final ReviewMapper mapper,
        final ReviewWriter writer) {
        this.reviews = reviews;
        this.bookIds = bookIds;
        this.bookSummaries = bookSummaries;
        this.communityRatings = communityRatings;
        this.mapper = mapper;
        this.writer = writer;
    }

    /**
     * Creates or edits the caller's review. Two concurrent first reviews conflict on the
     * {@code (user_id, book_id)} unique constraint; the winner's row is there to load on the retry,
     * so {@link ConflictRetry} re-runs the write in {@link ReviewWriter}'s fresh transaction.
     */
    @Override
    public ReviewResponse upsert(
        final Long userId, final String bookKey, final UpsertReviewRequest request) {
        final long bookId = bookIds.requireBookId(bookKey);
        return ConflictRetry.retryOnConflict(MAX_UPSERT_ATTEMPTS, LOG,
            "review.upsert conflict, retrying userId=" + userId + " bookId=" + bookId,
            () -> writer.upsert(userId, bookId, bookKey, request));
    }

    @Override
    public void remove(final Long userId, final String bookKey) {
        writer.remove(userId, bookIds.requireBookId(bookKey));
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPage listForBook(final String bookKey, final PageQuery page) {
        final Page<Review> found = reviews.findForBook(
            bookIds.requireBookId(bookKey), page.toPageable());
        final List<ReviewResponse> responses = found.getContent().stream()
            .map(review -> mapper.toResponse(review, bookKey))
            .toList();
        return new ReviewPage(responses, found.getTotalElements(), page.getOffset(), page.getLimit());
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPage listOwn(final Long userId, final PageQuery page) {
        final Page<Review> found = reviews.findForUser(userId, page.toPageable());
        final Map<Long, BookSummary> booksById = bookSummaries.summariesByIds(
                found.getContent().stream().map(Review::getBookId).toList())
            .stream()
            .collect(Collectors.toMap(BookSummary::bookId, Function.identity()));
        final List<ReviewResponse> responses = found.getContent().stream()
            .map(review -> mapper.toResponse(review, keyOf(review, booksById)))
            .toList();
        return new ReviewPage(responses, found.getTotalElements(), page.getOffset(), page.getLimit());
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityRatingResponse communityRating(final String bookKey) {
        final BookCommunityRating book = communityRatings.byKey(bookKey)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + bookKey));
        final Map<Integer, Long> countByStar = reviews.countByStarForBook(book.bookId()).stream()
            .collect(Collectors.toMap(RatingBucket::star, RatingBucket::count));
        final List<StarCount> distribution = IntStream.iterate(
                HIGHEST_STAR, star -> star >= LOWEST_STAR, star -> star - 1)
            .mapToObj(star -> new StarCount(star, countByStar.getOrDefault(star, 0L)))
            .toList();
        return new CommunityRatingResponse(
            book.average(), book.count(), distribution);
    }

    private static String keyOf(final Review review, final Map<Long, BookSummary> booksById) {
        final BookSummary book = booksById.get(review.getBookId());
        if (book == null) {
            throw new IllegalStateException(
                "review references a missing book bookId=" + review.getBookId());
        }
        return book.dedupKey();
    }
}
