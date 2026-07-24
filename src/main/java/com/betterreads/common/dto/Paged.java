package com.betterreads.common.dto;

import java.util.List;

/**
 * A paged collection response. The items serialize as {@link ApiResponse}'s {@code data} and the
 * counts as its {@code meta}.
 *
 * @param <T> the item type
 */
public interface Paged<T> {

    /** The items on this page. */
    List<T> items();

    /** The total number of items across all pages. */
    long total();

    /** The zero-based offset of the first item on this page. */
    int offset();

    /** The page size. */
    int limit();
}
