package com.betterreads.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pagination metadata on a collection response, in the {@code meta} member of {@link ApiResponse}.
 * Absent on a single-resource response.
 *
 * @param total the total number of items across all pages
 * @param offset the zero-based offset of the first item on this page
 * @param limit the page size
 */
public record ResponseMeta(
    @Schema(example = "142") long total,
    @Schema(example = "0") int offset,
    @Schema(example = "20") int limit
) {
}
