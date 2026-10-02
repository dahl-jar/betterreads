package com.betterreads.features.shelves;

import java.util.List;

import com.betterreads.errors.AuthenticatedBookErrorResponses;
import org.springframework.http.ProblemDetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own reading shelf, with books addressed by the same public key as search and detail. */
@RestController
@RequestMapping("/api/v1/me/books")
@Tag(name = "Shelf", description = "The current user's reading shelf")
class ShelfController {

    private final ShelfService shelfService;

    ShelfController(final ShelfService shelfService) {
        this.shelfService = shelfService;
    }

    @GetMapping
    @Operation(summary = "List the current user's shelf")
    @ApiResponse(responseCode = "200", description = "The shelf, newest first")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<ShelfEntryResponse> list(
        @AuthenticationPrincipal final Long userId,
        @RequestParam(name = "status", required = false) final @Nullable ReadingStatus status) {
        return shelfService.list(userId, status);
    }

    @PutMapping("/{key}/status")
    @Operation(summary = "Set the shelf status for a book")
    @ApiResponse(responseCode = "200", description = "The updated shelf entry")
    @ApiResponse(responseCode = "400", description = "Unknown status value",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @AuthenticatedBookErrorResponses
    public ShelfEntryResponse changeStatus(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key,
        @Valid @RequestBody final SetStatusRequest request) {
        return shelfService.changeStatus(userId, key, request.status());
    }

    @PutMapping("/{key}/favorite")
    @Operation(summary = "Set the favorite flag for a book")
    @ApiResponse(responseCode = "200", description = "The updated shelf entry")
    @AuthenticatedBookErrorResponses
    public ShelfEntryResponse markFavorite(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key,
        @Valid @RequestBody final SetFavoriteRequest request) {
        return shelfService.markFavorite(userId, key, request.favorite());
    }

    @PatchMapping("/{key}")
    @Operation(summary = "Update reading dates and note")
    @ApiResponse(responseCode = "200", description = "The updated shelf entry")
    @ApiResponse(responseCode = "400", description = "Finished date before started date",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Book is not on the shelf",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ShelfEntryResponse update(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key,
        @Valid @RequestBody final UpdateEntryRequest request) {
        return shelfService.updateEntry(userId, key, request);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Remove a book from the shelf")
    @ApiResponse(responseCode = "204", description = "Book removed or already absent")
    @AuthenticatedBookErrorResponses
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key) {
        shelfService.remove(userId, key);
        return ResponseEntity.noContent().build();
    }
}
