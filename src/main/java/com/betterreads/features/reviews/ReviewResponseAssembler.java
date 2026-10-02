package com.betterreads.features.reviews;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.bookaccess.BookSummary;
import com.betterreads.comments.ReviewCommentCounts;
import com.betterreads.users.UsernameLookup;

import org.springframework.stereotype.Component;

@Component
class ReviewResponseAssembler {

    private final UsernameLookup usernames;

    private final ReviewCommentCounts commentCounts;

    public ReviewResponseAssembler(final UsernameLookup usernames, final ReviewCommentCounts commentCounts) {
        this.usernames = usernames;
        this.commentCounts = commentCounts;
    }

    public List<ReviewResponse> assemble(final List<Review> rows, final Function<Review, String> bookKeyOf) {
        final Map<Long, String> authors = usernames.usernamesByIds(
            rows.stream().map(Review::getUserId).distinct().toList());
        final Map<Long, Long> countsByReviewId = commentCounts.countsByReviewIds(
            rows.stream().map(Review::getReviewId).toList());
        return rows.stream()
            .map(review -> toResponse(review, bookKeyOf.apply(review),
                UsernameLookup.usernameIn(authors, review.getUserId()),
                countsByReviewId.getOrDefault(review.getReviewId(), 0L)))
            .toList();
    }

    public ReviewResponse assembleOne(final Review review, final String bookKey) {
        return assemble(List.of(review), row -> bookKey).getFirst();
    }

    static BookSummary bookOf(final Review review, final Map<Long, BookSummary> booksById) {
        return Optional.ofNullable(booksById.get(review.getBookId()))
            .orElseThrow(() -> new IllegalStateException(
                "review references a missing book bookId=" + review.getBookId()));
    }

    private static ReviewResponse toResponse(
        final Review review, final String bookKey, final String author, final long commentCount) {
        return new ReviewResponse(
            review.getReviewId(),
            bookKey,
            review.getRating(),
            review.getTitle(),
            review.getBody(),
            review.getCreatedAt().toLocalDate(),
            author,
            commentCount);
    }
}
