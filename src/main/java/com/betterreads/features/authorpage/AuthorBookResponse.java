package com.betterreads.features.authorpage;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

public record AuthorBookResponse(
    String bookId,
    String title,
    @Nullable String coverUrl,
    @Nullable Integer firstPublishYear,
    @Nullable String seriesName,
    @Nullable BigDecimal seriesPosition,
    String role
) {
}
