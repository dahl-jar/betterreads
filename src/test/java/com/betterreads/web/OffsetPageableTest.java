package com.betterreads.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;

class OffsetPageableTest {

    private static final int LIMIT = 10;

    private static final long OFFSET = 25;

    private static final long NEXT_OFFSET = 35;

    private static final long PREVIOUS_OFFSET = 15;

    private static final long NEAR_START_OFFSET = 5;

    private static final int THIRD_PAGE = 2;

    private static final long THIRD_PAGE_OFFSET = 20;

    private final OffsetPageable page = new OffsetPageable(OFFSET, LIMIT);

    @Test
    void shouldStepForwardByLimit() {
        final Pageable next = page.next();

        assertThat(next.getOffset()).isEqualTo(NEXT_OFFSET);
    }

    @Test
    void shouldStepBackByLimit() {
        final Pageable previous = page.previous();

        assertThat(previous.getOffset()).isEqualTo(PREVIOUS_OFFSET);
    }

    @Test
    void shouldStopAtZeroWhenSteppingBackPastStart() {
        final OffsetPageable nearStart = new OffsetPageable(NEAR_START_OFFSET, LIMIT);

        final Pageable previous = nearStart.previous();

        assertThat(previous.getOffset()).isZero();
    }

    @Test
    void shouldStartFirstPageAtZero() {
        final Pageable first = page.first();

        assertThat(first.getOffset()).isZero();
    }

    @Test
    void shouldMapPageNumberToOffset() {
        final Pageable third = page.withPage(THIRD_PAGE);

        assertThat(third.getOffset()).isEqualTo(THIRD_PAGE_OFFSET);
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, true"})
    void shouldHavePreviousOnlyAfterFirstItem(final long offset, final boolean expected) {
        final OffsetPageable pageable = new OffsetPageable(offset, LIMIT);

        final boolean hasPrevious = pageable.hasPrevious();

        assertThat(hasPrevious).isEqualTo(expected);
    }
}
