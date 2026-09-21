package com.betterreads.search.controller;

import com.betterreads.catalog.service.pipeline.SearchMissStager;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.dto.SearchOutcome;
import com.betterreads.search.service.BookSearchService;
import com.betterreads.search.sse.SearchHitEmitters;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * Catalog search endpoint backed by Meilisearch.
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@RequiredArgsConstructor
@Tag(name = "Search", description = "Catalog full-text search")
@SecurityRequirements
public class BookSearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_QUERY_LENGTH = 200;

    private final BookSearchService searchService;

    private final SearchMissStager searchMissStager;

    private final SearchHitEmitters searchHitEmitters;

    @GetMapping(value = "/books/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream books that finish staging and match the query")
    public SseEmitter streamHits(@RequestParam("q") @NotBlank @Size(max = MAX_QUERY_LENGTH) final String query) {
        return searchHitEmitters.open(query);
    }

    /** Returns books matching the query, ordered by relevance. */
    @GetMapping("/books")
    @Operation(summary = "Search the book catalog")
    public BookSearchResult search(
        @RequestParam("q") @NotBlank @Size(max = MAX_QUERY_LENGTH) final String query,
        @RequestParam(value = "offset", defaultValue = "0") @Min(0) final int offset,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) final int limit
    ) {
        final SearchOutcome outcome = searchService.searchOutcome(query, offset, limit);
        if (!outcome.degraded() && offset == 0) {
            searchMissStager.stage(query);
        }
        return outcome.result();
    }
}
