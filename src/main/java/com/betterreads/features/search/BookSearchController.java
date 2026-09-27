package com.betterreads.features.search;

import com.betterreads.searchmiss.SearchMissSink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Catalog search endpoints. */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@RequiredArgsConstructor
@Tag(name = "Search", description = "Catalog full-text search")
@SecurityRequirements
class BookSearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_QUERY_LENGTH = 200;

    private final BookSearchService searchService;

    private final SearchMissSink searchMissSink;

    private final SearchHitEmitters searchHitEmitters;

    @GetMapping(value = "/books/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream newly added books that match the query")
    @ApiResponse(responseCode = "200",
        description = "A search-hit event for each newly added book matching q.",
        content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            schema = @Schema(implementation = BookSearchDocument.class)))
    public SseEmitter streamHits(@RequestParam("q") @NotBlank @Size(max = MAX_QUERY_LENGTH) final String query) {
        return searchHitEmitters.open(query);
    }

    @GetMapping("/books")
    @Operation(summary = "Search the book catalog")
    public BookSearchResult search(
        @RequestParam("q") @NotBlank @Size(max = MAX_QUERY_LENGTH) final String query,
        @RequestParam(value = "offset", defaultValue = "0") @Min(0) final int offset,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) final int limit
    ) {
        final SearchOutcome outcome = searchService.search(query, offset, limit);
        if (!outcome.degraded() && offset == 0) {
            searchMissSink.stage(query);
        }
        return outcome.result();
    }
}
