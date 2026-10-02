package com.betterreads.features.booklists;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
@Validated
@Tag(name = "Catalog", description = "Public book detail and lists")
@SecurityRequirements
class BookListController {

    private static final int MAX_LIST_SIZE = 50;

    private static final String DEFAULT_LIST_SIZE = "12";

    private final BookListService bookListService;

    private final BookSeriesReader bookSeriesReader;

    BookListController(final BookListService bookListService, final BookSeriesReader bookSeriesReader) {
        this.bookListService = bookListService;
        this.bookSeriesReader = bookSeriesReader;
    }

    @GetMapping("/count")
    @Operation(summary = "Count the books in the catalog")
    public BookCountResponse count() {
        return bookListService.count();
    }

    @GetMapping
    @Operation(summary = "Get a homepage book list")
    public List<BookCardResponse> list(
        @RequestParam("list") final BookListType list,
        @RequestParam(value = "limit", defaultValue = DEFAULT_LIST_SIZE) @Min(1) @Max(MAX_LIST_SIZE) final int limit) {
        return bookListService.list(list, limit);
    }

    @GetMapping("/{key}/series")
    @Operation(summary = "List the other books in each of a book's series")
    @ApiResponse(responseCode = "200", description = "One entry per series the book belongs to")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<BookSeriesResponse> series(@PathVariable final String key) {
        return bookSeriesReader.seriesOf(key);
    }
}
