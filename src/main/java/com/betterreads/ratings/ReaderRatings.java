package com.betterreads.ratings;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ReaderRatings {

    Optional<Integer> ratingOf(long userId, long bookId);

    Map<Long, Integer> ratingsOf(long userId, Collection<Long> bookIds);
}
