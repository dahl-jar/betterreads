package com.betterreads.comments;

import java.util.Collection;
import java.util.Map;

@FunctionalInterface
public interface ReviewCommentCounts {

    Map<Long, Long> countsByReviewIds(Collection<Long> reviewIds);
}
