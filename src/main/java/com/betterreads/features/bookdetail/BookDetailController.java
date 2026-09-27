package com.betterreads.features.bookdetail;

import com.betterreads.errors.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Public catalog book detail. */
@RestController
@RequestMapping("/api/v1/books")
@Validated
@Tag(name = "Catalog", description = "Public book detail and lists")
@SecurityRequirements
class BookDetailController {

    private static final String NOT_FOUND_PREFIX = "No book with key ";

    private final BookDetailService bookDetailService;

    private final BookUpdateEmitters emitters;

    public BookDetailController(final BookDetailService bookDetailService, final BookUpdateEmitters emitters) {
        this.bookDetailService = bookDetailService;
        this.emitters = emitters;
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get book detail by key")
    @ApiResponse(responseCode = "200", description = "The book")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public BookDetailResponse getBook(@PathVariable final String key) {
        return requireBook(key);
    }

    @GetMapping(value = "/{key}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream book detail updates by key")
    @ApiResponse(responseCode = "200",
        description = "One book-updated event with the full detail.",
        content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            schema = @Schema(implementation = BookDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public SseEmitter streamBook(@PathVariable final String key) {
        return emitters.open(key, requireBook(key), () -> bookDetailService.findByKey(key));
    }

    private BookDetailResponse requireBook(final String key) {
        return bookDetailService.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_PREFIX + key));
    }
}
