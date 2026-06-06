package com.betterreads.search.controller;

import com.betterreads.catalog.service.pipeline.SearchMissStager;
import com.betterreads.search.dto.BookSearchResult;
import com.betterreads.search.dto.SearchOutcome;
import com.betterreads.search.service.BookSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final BookSearchService searchService;

    private final SearchMissStager searchMissStager;

    /**
     * Returns books matching the query, ordered by relevance.
     *
     * <p>The first page of every successful search also resolves the query's series and author in the
     * background, so a query that returns only a few hits (such as an author whose catalog is mostly
     * unstaged) still fills in the rest for a later search. The stager dedupes per query, so a
     * paginated or repeated search resolves the query at most once per dedup window. A degraded
     * search (Meilisearch down) does not trigger it, and only the first page does so a "load more"
     * fetch is not a fresh trigger.
     */
    @GetMapping("/books")
    @Operation(summary = "Search the book catalog")
    public BookSearchResult search(
        @RequestParam("q") @NotBlank @Size(max = 200) final String query,
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
