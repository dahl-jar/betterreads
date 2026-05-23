package com.betterreads.search.controller;

import com.betterreads.search.dto.BookSearchResult;
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

    private final BookSearchService searchService;

    /**
     * Returns books matching the query, ordered by relevance.
     */
    @GetMapping("/books")
    @Operation(summary = "Search the book catalog")
    public BookSearchResult search(
        @RequestParam("q") @NotBlank @Size(max = 200) final String query,
        @RequestParam(value = "offset", defaultValue = "0") @Min(0) final int offset,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(50) final int limit
    ) {
        return searchService.search(query, offset, limit);
    }
}
