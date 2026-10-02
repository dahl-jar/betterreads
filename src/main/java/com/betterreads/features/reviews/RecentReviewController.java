package com.betterreads.features.reviews;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Reviews", description = "Book reviews and ratings")
@SecurityRequirements
class RecentReviewController {

    private static final int MAX_LIMIT = 20;

    private static final String DEFAULT_LIMIT = "6";

    private final RecentReviewReader recentReviews;

    RecentReviewController(final RecentReviewReader recentReviews) {
        this.recentReviews = recentReviews;
    }

    @GetMapping("/api/v1/reviews/recent")
    @Operation(summary = "List the newest reviews with text")
    @ApiResponse(responseCode = "200", description = "Reviews with text, newest first")
    @ApiResponse(responseCode = "400", description = "Limit out of range",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<RecentReviewResponse> recent(
        @RequestParam(value = "limit", defaultValue = DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) final int limit) {
        return recentReviews.recent(limit);
    }
}
