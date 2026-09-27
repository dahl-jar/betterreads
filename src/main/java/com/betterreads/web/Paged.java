package com.betterreads.web;

import java.util.List;

/** Paged collection response, items go out as {@code data} and the counts as {@code meta}. */
public interface Paged<T> {

    List<T> items();

    /** item count across all pages */
    long total();

    /** zero-based index of the first item on this page */
    int offset();

    /** page size */
    int limit();
}
