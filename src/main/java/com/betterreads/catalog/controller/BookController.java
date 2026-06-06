package com.betterreads.catalog.controller;

import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.read.sse.BookUpdateEmitters;
import com.betterreads.catalog.service.read.BookReadService;
import com.betterreads.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public catalog book detail. The promoted book is served when present, otherwise the staging seed.
 */
@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Catalog", description = "Public book detail")
@SecurityRequirements
public class BookController {

    private static final String NOT_FOUND_PREFIX = "No book with key ";

    private final BookReadService bookReadService;

    private final BookUpdateEmitters emitters;

    public BookController(final BookReadService bookReadService, final BookUpdateEmitters emitters) {
        this.bookReadService = bookReadService;
        this.emitters = emitters;
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
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_PREFIX + key));
    }

    /**
     * Streams the book's fill-in: a complete book is sent at once, a cold book's stream stays open
     * until enrichment writes it.
     *
     * @param key a source identifier shared with search results
     * @throws ResourceNotFoundException when no promoted book or staging seed has the key
     */
    @GetMapping(value = "/{key}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream book detail updates by key")
    public SseEmitter streamBook(@PathVariable final String key) {
        final BookDetailResponse detail = bookReadService.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_PREFIX + key));
        return emitters.open(key, detail, detail.complete(), () -> bookReadService.findByKey(key));
    }
}
