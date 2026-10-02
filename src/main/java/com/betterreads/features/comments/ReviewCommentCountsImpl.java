package com.betterreads.features.comments;

import com.betterreads.comments.ReviewCommentCounts;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReviewCommentCountsImpl implements ReviewCommentCounts {

    private final CommentRepository comments;

    ReviewCommentCountsImpl(final CommentRepository comments) {
        this.comments = comments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countsByReviewIds(final Collection<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return comments.countTopLevelForTargets(CommentTarget.REVIEW, reviewIds).stream()
            .collect(Collectors.toMap(TargetCount::targetId, TargetCount::count));
    }
}
