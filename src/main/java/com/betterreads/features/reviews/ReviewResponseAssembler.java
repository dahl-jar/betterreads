package com.betterreads.features.reviews;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.betterreads.bookaccess.BookSummary;
import com.betterreads.comments.ReviewCommentCounts;
import com.betterreads.shelfaccess.ReadingDates;
import com.betterreads.shelfaccess.ReadingDatesLookup;
import com.betterreads.users.UsernameLookup;

import org.springframework.stereotype.Component;

@Component
class ReviewResponseAssembler {

    private final UsernameLookup usernames;

    private final ReviewCommentCounts commentCounts;

    private final ReadingDatesLookup readingDates;

    public ReviewResponseAssembler(
        final UsernameLookup usernames,
        final ReviewCommentCounts commentCounts,
        final ReadingDatesLookup readingDates) {
        this.usernames = usernames;
        this.commentCounts = commentCounts;
        this.readingDates = readingDates;
    }

    public List<ReviewResponse> assemble(final List<Review> rows, final Function<Review, String> bookKeyOf) {
        final ReviewLookupResults lookups = lookupResultsFor(rows);
        return rows.stream()
            .map(review -> toResponse(review, bookKeyOf.apply(review), lookups))
            .toList();
    }

    public ReviewResponse assembleOne(final Review review, final String bookKey) {
        return assemble(List.of(review), row -> bookKey).getFirst();
    }

    ReviewLookupResults lookupResultsFor(final List<Review> rows) {
        return new ReviewLookupResults(
            this.usernames.usernamesByIds(rows.stream().map(Review::getUserId).distinct().toList()),
            this.commentCounts.countsByReviewIds(rows.stream().map(Review::getReviewId).toList()),
            this.readingDates.datesFor(rows.stream().map(ReviewLookupResults::readerBookOf).toList()));
    }

    static BookSummary bookOf(final Review review, final Map<Long, BookSummary> booksById) {
        return Optional.ofNullable(booksById.get(review.getBookId()))
            .orElseThrow(() -> new IllegalStateException(
                "review references a missing book bookId=" + review.getBookId()));
    }

    private static ReviewResponse toResponse(
        final Review review, final String bookKey, final ReviewLookupResults lookups) {
        final ReadingDates dates = lookups.readingDatesOf(review);
        return new ReviewResponse(
            review.getReviewId(),
            bookKey,
            review.getRating(),
            review.getTitle(),
            review.getBody(),
            review.getCreatedAt().toLocalDate(),
            lookups.authorOf(review),
            lookups.commentCountOf(review),
            dates.startedAt(),
            dates.finishedAt());
    }
}
