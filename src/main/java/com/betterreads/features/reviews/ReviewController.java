package com.betterreads.features.reviews;

import com.betterreads.errors.AuthenticatedBookErrorResponses;
import com.betterreads.web.PageQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Book reviews and ratings. Reads are public, writes only touch the caller's own review. */
@RestController
@Tag(name = "Reviews", description = "Book reviews and ratings")
class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(final ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/v1/books/{key}/reviews")
    @Operation(summary = "List a book's reviews")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "A page of reviews")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ReviewPage listForBook(
        @PathVariable final String key,
        @Valid @ParameterObject final PageQuery page) {
        return reviewService.listForBook(key, page);
    }

    @GetMapping("/api/v1/books/{key}/community-rating")
    @Operation(summary = "Get a book's community rating breakdown")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "The community rating")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommunityRatingResponse communityRating(@PathVariable final String key) {
        return reviewService.communityRating(key);
    }

    @PutMapping("/api/v1/books/{key}/reviews/me")
    @Operation(summary = "Post or edit the caller's review of a book")
    @ApiResponse(responseCode = "200", description = "The saved review")
    @ApiResponse(responseCode = "400", description = "Rating out of range or text too long",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @AuthenticatedBookErrorResponses
    public ReviewResponse upsert(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key,
        @Valid @RequestBody final UpsertReviewRequest request) {
        return reviewService.upsert(userId, key, request);
    }

    @DeleteMapping("/api/v1/books/{key}/reviews/me")
    @Operation(summary = "Remove the caller's review of a book")
    @ApiResponse(responseCode = "204", description = "Review removed or already absent")
    @AuthenticatedBookErrorResponses
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key) {
        reviewService.remove(userId, key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/me/reviews")
    @Operation(summary = "List the caller's reviews")
    @ApiResponse(responseCode = "200", description = "A page of the caller's reviews")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ReviewPage listOwn(
        @AuthenticationPrincipal final Long userId,
        @Valid @ParameterObject final PageQuery page) {
        return reviewService.listOwn(userId, page);
    }
}
