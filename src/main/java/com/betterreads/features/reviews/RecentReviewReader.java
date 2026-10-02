package com.betterreads.features.reviews;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.betterreads.bookaccess.BookSummary;
import com.betterreads.bookaccess.BookSummaryReader;
import com.betterreads.users.UsernameLookup;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecentReviewReader {

    private final ReviewRepository reviews;

    private final UsernameLookup usernames;

    private final BookSummaryReader bookSummaries;

    RecentReviewReader(
        final ReviewRepository reviews,
        final UsernameLookup usernames,
        final BookSummaryReader bookSummaries) {
        this.reviews = reviews;
        this.usernames = usernames;
        this.bookSummaries = bookSummaries;
    }

    @Transactional(readOnly = true)
    public List<RecentReviewResponse> recent(final int limit) {
        final List<Review> rows = reviews.findRecentWithBody(PageRequest.of(0, limit));
        final Map<Long, String> authors = usernames.usernamesByIds(
            rows.stream().map(Review::getUserId).distinct().toList());
        final Map<Long, BookSummary> booksById = bookSummaries.summariesKeyedById(
            rows.stream().map(Review::getBookId).distinct().toList());
        return rows.stream()
            .map(review -> toResponse(
                review,
                UsernameLookup.usernameIn(authors, review.getUserId()),
                ReviewResponseAssembler.bookOf(review, booksById)))
            .toList();
    }

    private static RecentReviewResponse toResponse(
        final Review review, final String author, final BookSummary book) {
        return new RecentReviewResponse(
            review.getReviewId(),
            author,
            review.getRating(),
            review.getTitle(),
            Objects.requireNonNull(review.getBody(),
                () -> "recent review has no body reviewId=" + review.getReviewId()),
            review.getCreatedAt().toLocalDate(),
            new ReviewedBook(book.dedupKey(), book.title(), book.authors(), book.servedCoverUrl()));
    }
}
