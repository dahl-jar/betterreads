package com.betterreads.common.dto;

import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} that pages by an arbitrary item offset, not a page number. Spring's
 * {@code PageRequest} only takes a page index, so an offset that is not a multiple of the limit
 * would silently snap to a page boundary and return the wrong rows.
 */
public final class OffsetPageable extends AbstractPageRequest {

    private static final long serialVersionUID = 1L;

    private final long offset;

    private final Sort sort;

    private OffsetPageable(final long offset, final int limit, final Sort sort) {
        super(0, limit);
        this.offset = offset;
        this.sort = sort;
    }

    /** A pageable starting at {@code offset}, returning up to {@code limit} unsorted items. */
    public static OffsetPageable of(final long offset, final int limit) {
        return new OffsetPageable(offset, limit, Sort.unsorted());
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + getPageSize(), getPageSize(), sort);
    }

    @Override
    public Pageable previous() {
        final long previousOffset = Math.max(0, offset - getPageSize());
        return new OffsetPageable(previousOffset, getPageSize(), sort);
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, getPageSize(), sort);
    }

    @Override
    public Pageable withPage(final int pageNumber) {
        return new OffsetPageable((long) pageNumber * getPageSize(), getPageSize(), sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
