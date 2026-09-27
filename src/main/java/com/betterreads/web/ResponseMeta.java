package com.betterreads.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Paging info in the {@code meta} member of a collection response. */
record ResponseMeta(
    @Schema(example = "142") long total,
    @Schema(example = "0") int offset,
    @Schema(example = "20") int limit
) {
}
