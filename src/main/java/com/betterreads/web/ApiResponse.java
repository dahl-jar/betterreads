package com.betterreads.web;

import org.jspecify.annotations.Nullable;

/** Body of every successful JSON response, meta holds paging on a collection and is null on a single resource. */
record ApiResponse<T>(
    T data,
    @Nullable ResponseMeta meta
) {
}
