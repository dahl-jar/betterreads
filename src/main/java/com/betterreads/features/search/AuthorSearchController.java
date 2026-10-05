package com.betterreads.features.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Validated
@Tag(name = "Search", description = "Catalog full-text search")
@SecurityRequirements
class AuthorSearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_QUERY_LENGTH = 200;

    private final AuthorSearchService searchService;

    AuthorSearchController(final AuthorSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/authors")
    @Operation(summary = "Search authors by name")
    public AuthorSearchResult searchAuthors(
        @RequestParam("q") @NotBlank @Size(max = MAX_QUERY_LENGTH) final String query,
        @RequestParam(value = "offset", defaultValue = "0") @Min(0) final int offset,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) final int limit
    ) {
        return searchService.search(query, offset, limit);
    }
}
