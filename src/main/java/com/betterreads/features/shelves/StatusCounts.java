package com.betterreads.features.shelves;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record StatusCounts(Map<ReadingStatus, Long> byStatus) {

    StatusCounts {
        byStatus = Map.copyOf(byStatus);
    }

    static StatusCounts from(final List<StatusCount> counts) {
        return new StatusCounts(counts.stream().collect(Collectors.toMap(StatusCount::status, StatusCount::count)));
    }

    long of(final ReadingStatus status) {
        return byStatus.getOrDefault(status, 0L);
    }
}
