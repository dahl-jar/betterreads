package com.betterreads.common.dto;

import java.util.List;

/**
 * A paged collection response. {@link com.betterreads.common.web.ApiResponseBodyAdvice} lifts the
 * items into the envelope's {@code data} and the counts into its {@code meta}, so a paged record
 * serializes as {@code {data: [...], meta: {...}}} like any other collection.
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
