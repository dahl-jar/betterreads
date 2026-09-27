package com.betterreads.features.comments;

import com.betterreads.web.PageQuery;
import org.springframework.http.ProblemDetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Comments on books and reviews. Reads are public, writes need an access token. */
@RestController
@Tag(name = "Comments", description = "Comments on books and reviews")
class CommentController {

    private final CommentService commentService;

    public CommentController(final CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/v1/books/{key}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a comment on a book")
    @ApiResponse(responseCode = "201", description = "The created comment")
    @CommentWriteErrorResponses
    @ApiResponse(responseCode = "404", description = "No book with that key, or no parent comment with that id",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommentResponse commentOnBook(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key,
        @Valid @RequestBody final CreateCommentRequest request) {
        return commentService.commentOnBook(userId, key, request);
    }

    @GetMapping("/api/v1/books/{key}/comments")
    @Operation(summary = "List a book's comments")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "A page of comments")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommentPage listForBook(
        @PathVariable final String key,
        @Valid @ParameterObject final PageQuery page) {
        return commentService.listForBook(key, page);
    }

    @PostMapping("/api/v1/reviews/{reviewId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a comment on a review")
    @ApiResponse(responseCode = "201", description = "The created comment")
    @CommentWriteErrorResponses
    @ApiResponse(responseCode = "404", description = "No review with that id, or no parent comment with that id",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommentResponse commentOnReview(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final Long reviewId,
        @Valid @RequestBody final CreateCommentRequest request) {
        return commentService.commentOnReview(userId, reviewId, request);
    }

    @GetMapping("/api/v1/reviews/{reviewId}/comments")
    @Operation(summary = "List a review's comments")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "A page of comments")
    @ApiResponse(responseCode = "404", description = "No review with that id",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommentPage listForReview(
        @PathVariable final Long reviewId,
        @Valid @ParameterObject final PageQuery page) {
        return commentService.listForReview(reviewId, page);
    }

    @GetMapping("/api/v1/comments/{commentId}/replies")
    @Operation(summary = "List a comment's replies")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "A page of replies")
    public CommentPage listReplies(
        @PathVariable final Long commentId,
        @Valid @ParameterObject final PageQuery page) {
        return commentService.listReplies(commentId, page);
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    @Operation(summary = "Remove the caller's comment")
    @ApiResponse(responseCode = "204", description = "Comment removed or already absent")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The comment belongs to another user",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final Long commentId) {
        commentService.remove(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
