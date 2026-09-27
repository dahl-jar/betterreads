package com.betterreads.features.reviews;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.betterreads.bookaccess.BookCommunityRating;
import com.betterreads.bookaccess.BookCommunityRatingReader;
import com.betterreads.bookaccess.BookIdLookup;
import com.betterreads.bookaccess.BookSummary;
import com.betterreads.bookaccess.BookSummaryReader;
import com.betterreads.db.ConflictRetry;
import com.betterreads.errors.ResourceNotFoundException;
import com.betterreads.web.PageQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReviewServiceImpl implements ReviewService {

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

    // PMD.ExcessiveParameterList: six injected collaborators, the constructor is Spring's injection point.
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
     * Two concurrent first reviews collide on the (user_id, book_id) unique key, so the loser
     * retries and edits the winner's row.
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
            .collect(Collectors.toMap(StarCount::star, StarCount::count));
        final List<StarCount> distribution = IntStream.iterate(
                HIGHEST_STAR, star -> star >= LOWEST_STAR, star -> star - 1)
            .mapToObj(star -> new StarCount(star, countByStar.getOrDefault(star, 0L)))
            .toList();
        return new CommunityRatingResponse(
            book.average(), book.count(), distribution);
    }

    private static String keyOf(final Review review, final Map<Long, BookSummary> booksById) {
        return Optional.ofNullable(booksById.get(review.getBookId()))
            .map(BookSummary::dedupKey)
            .orElseThrow(() -> new IllegalStateException(
                "review references a missing book bookId=" + review.getBookId()));
    }
}
