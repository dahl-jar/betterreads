package com.betterreads.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.data.domain.Pageable;

/**
 * Offset and limit query parameters for a paged list endpoint, bound and validated once so endpoints
 * take a single parameter.
 */
// PMD.DataClass: Spring binds query params through the setters; the getters/setters are required.
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

    /** A pageable honoring the raw offset, so an offset that is not a multiple of the limit is exact. */
    public Pageable toPageable() {
        return OffsetPageable.of(offset, limit);
    }
}
