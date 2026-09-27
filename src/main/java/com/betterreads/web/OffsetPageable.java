package com.betterreads.web;

import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pages by raw item offset.
 * PageRequest only takes a page index, so an offset that isn't a multiple of the limit would snap to a
 * page boundary and return the wrong rows
 */
final class OffsetPageable extends AbstractPageRequest {

    private static final long serialVersionUID = 1L;

    private final long offset;

    OffsetPageable(final long offset, final int limit) {
        super(0, limit);
        this.offset = offset;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + getPageSize(), getPageSize());
    }

    @Override
    public Pageable previous() {
        return new OffsetPageable(Math.max(0, offset - getPageSize()), getPageSize());
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, getPageSize());
    }

    @Override
    public Pageable withPage(final int pageNumber) {
        return new OffsetPageable((long) pageNumber * getPageSize(), getPageSize());
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
