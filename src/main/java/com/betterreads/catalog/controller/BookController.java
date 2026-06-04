package com.betterreads.catalog.controller;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.service.read.BookReadService;
import com.betterreads.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public catalog book detail. The promoted book is served when present, otherwise the staging seed,
 * so a just-staged book is still readable while enrichment runs.
 */
@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Catalog", description = "Public book detail")
@SecurityRequirements
public class BookController {

    private final BookReadService bookReadService;

    public BookController(final BookReadService bookReadService) {
        this.bookReadService = bookReadService;
    }

    /**
     * Returns the detail for the book with the given key.
     *
     * @param key a source identifier shared with search results
     * @throws ResourceNotFoundException when no promoted book or staging seed has the key
     */
    @GetMapping("/{key}")
    @Operation(summary = "Get book detail by key")
    public BookDetailResponse getBook(@PathVariable final String key) {
        return bookReadService.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + key));
    }
}
