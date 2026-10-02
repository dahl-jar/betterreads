package com.betterreads.features.shelves;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Catalog", description = "Public book detail and lists")
@SecurityRequirements
class ShelfCountController {

    private final ShelfService shelfService;

    ShelfCountController(final ShelfService shelfService) {
        this.shelfService = shelfService;
    }

    @GetMapping("/api/v1/books/{key}/shelf-counts")
    @Operation(summary = "Count a book's readers per shelf status")
    @ApiResponse(responseCode = "200", description = "The reader count for each shelf status")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ShelfCountsResponse shelfCounts(@PathVariable final String key) {
        return shelfService.countsForBook(key);
    }
}
