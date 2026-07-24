package com.betterreads.catalog.controller;

import com.betterreads.catalog.dto.BookCardResponse;
import com.betterreads.catalog.dto.BookDetailResponse;
import com.betterreads.catalog.read.sse.BookUpdateEmitters;
import com.betterreads.catalog.service.read.BookListService;
import com.betterreads.catalog.service.read.BookListType;
import com.betterreads.catalog.service.read.BookReadService;
import com.betterreads.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Public catalog book detail and homepage lists. */
@RestController
@RequestMapping("/api/v1/books")
@Validated
@Tag(name = "Catalog", description = "Public book detail and lists")
@SecurityRequirements
public class BookController {

    private static final String NOT_FOUND_PREFIX = "No book with key ";

    private static final int MAX_LIST_SIZE = 50;

    private final BookReadService bookReadService;

    private final BookListService bookListService;

    private final BookUpdateEmitters emitters;

    public BookController(
        final BookReadService bookReadService,
        final BookListService bookListService,
        final BookUpdateEmitters emitters) {
        this.bookReadService = bookReadService;
        this.bookListService = bookListService;
        this.emitters = emitters;
    }

    /** @param limit the maximum number of cards, capped at {@value #MAX_LIST_SIZE} */
    @GetMapping
    @Operation(summary = "Get a homepage book list")
    public List<BookCardResponse> list(
        @RequestParam("list") final BookListType list,
        @RequestParam(value = "limit", defaultValue = "12") @Min(1) @Max(MAX_LIST_SIZE) final int limit) {
        return bookListService.list(list, limit);
    }

    /**
     * @param key a source identifier shared with search results
     * @throws ResourceNotFoundException when no book has the key
     */
    @GetMapping("/{key}")
    @Operation(summary = "Get book detail by key")
    public BookDetailResponse getBook(@PathVariable final String key) {
        return bookReadService.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_PREFIX + key));
    }

    /**
     * Streams book detail updates. A complete book is sent at once; an incomplete book holds the
     * stream open until its missing fields are written.
     *
     * @param key a source identifier shared with search results
     * @throws ResourceNotFoundException when no book has the key
     */
    @GetMapping(value = "/{key}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream book detail updates by key")
    public SseEmitter streamBook(@PathVariable final String key) {
        final BookDetailResponse detail = bookReadService.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_PREFIX + key));
        return emitters.open(key, detail, detail.complete(), () -> bookReadService.findByKey(key));
    }
}
