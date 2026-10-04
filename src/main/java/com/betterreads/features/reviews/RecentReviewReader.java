package com.betterreads.features.reviews;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.betterreads.bookaccess.BookSummary;
import com.betterreads.bookaccess.BookSummaryReader;
import com.betterreads.shelfaccess.ReadingDates;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecentReviewReader {

    private final ReviewRepository reviews;

    private final ReviewResponseAssembler assembler;

    private final BookSummaryReader bookSummaries;

    RecentReviewReader(
        final ReviewRepository reviews,
        final ReviewResponseAssembler assembler,
        final BookSummaryReader bookSummaries) {
        this.reviews = reviews;
        this.assembler = assembler;
        this.bookSummaries = bookSummaries;
    }

    @Transactional(readOnly = true)
    public List<RecentReviewResponse> recent(final int limit) {
        final List<Review> rows = this.reviews.findRecentWithBody(PageRequest.of(0, limit));
        final ReviewLookupResults lookups = this.assembler.lookupResultsFor(rows);
        final Map<Long, BookSummary> booksById = this.bookSummaries.summariesKeyedById(
            rows.stream().map(Review::getBookId).distinct().toList());
        return rows.stream()
            .map(review -> toResponse(review, lookups, ReviewResponseAssembler.bookOf(review, booksById)))
            .toList();
    }

    private static RecentReviewResponse toResponse(
        final Review review, final ReviewLookupResults lookups, final BookSummary book) {
        final ReadingDates dates = lookups.readingDatesOf(review);
        return new RecentReviewResponse(
            review.getReviewId(),
            lookups.authorOf(review),
            review.getRating(),
            review.getTitle(),
            Objects.requireNonNull(review.getBody(),
                () -> "recent review has no body reviewId=" + review.getReviewId()),
            review.getCreatedAt().toLocalDate(),
            new ReviewedBook(book.dedupKey(), book.title(), book.authors(), book.servedCoverUrl()),
            lookups.commentCountOf(review),
            dates.startedAt(),
            dates.finishedAt());
    }
}
