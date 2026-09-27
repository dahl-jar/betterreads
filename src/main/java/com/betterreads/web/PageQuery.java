package com.betterreads.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.data.domain.Pageable;

/** Offset and limit query params, bound and validated once so a paged endpoint takes one parameter. */
// PMD.DataClass: Spring binds query params through the setters, so the getters and setters stay.
@SuppressWarnings("PMD.DataClass")
public class PageQuery {

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    @PositiveOrZero
    private int offset;

    @Min(1)
    @Max(MAX_LIMIT)
    private int limit = DEFAULT_LIMIT;

    public int getOffset() {
        return offset;
    }

    public void setOffset(final int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(final int limit) {
        this.limit = limit;
    }

    public Pageable toPageable() {
        return new OffsetPageable(offset, limit);
    }
}
