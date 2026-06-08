package com.betterreads.comments.controller;

import com.betterreads.comments.dto.CommentPage;
import com.betterreads.comments.dto.CommentResponse;
import com.betterreads.comments.dto.CreateCommentRequest;
import com.betterreads.comments.service.CommentService;
import com.betterreads.common.dto.PageQuery;
import org.springframework.http.ProblemDetail;
import com.betterreads.common.web.AuthenticatedBookErrorResponses;
import com.betterreads.common.web.AuthenticatedReviewErrorResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

/**
 * Comments on books and reviews. Reading a target's comments is public; posting a comment or reply
 * and deleting one's own comment require an access token. Listings are paged.
 */
@RestController
@Tag(name = "Comments", description = "Comments on books and reviews")
public class CommentController {

    private final CommentService commentService;

    public CommentController(final CommentService commentService) {
        this.commentService = commentService;
    }

    /** Posts a comment or reply on a book. */
    @PostMapping("/api/v1/books/{key}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a comment on a book")
    @ApiResponse(responseCode = "201", description = "The created comment")
    @ApiResponse(responseCode = "400", description = "Empty body or reply to a reply",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @AuthenticatedBookErrorResponses
    public CommentResponse commentOnBook(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final String key,
        @Valid @RequestBody final CreateCommentRequest request) {
        return commentService.commentOnBook(userId, key, request);
    }

    /** Returns a page of a book's top-level comments. */
    @GetMapping("/api/v1/books/{key}/comments")
    @Operation(summary = "List a book's comments")
    @ApiResponse(responseCode = "200", description = "A page of comments")
    @ApiResponse(responseCode = "404", description = "No book with that key",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommentPage listForBook(
        @PathVariable final String key,
        @Valid @ParameterObject final PageQuery page) {
        return commentService.listForBook(key, page);
    }

    /** Posts a comment or reply on a review. */
    @PostMapping("/api/v1/reviews/{reviewId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a comment on a review")
    @ApiResponse(responseCode = "201", description = "The created comment")
    @ApiResponse(responseCode = "400", description = "Empty body or reply to a reply",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @AuthenticatedReviewErrorResponses
    public CommentResponse commentOnReview(
        @AuthenticationPrincipal final Long userId,
        @PathVariable final Long reviewId,
        @Valid @RequestBody final CreateCommentRequest request) {
        return commentService.commentOnReview(userId, reviewId, request);
    }

    /** Returns a page of a review's top-level comments. */
    @GetMapping("/api/v1/reviews/{reviewId}/comments")
    @Operation(summary = "List a review's comments")
    @ApiResponse(responseCode = "200", description = "A page of comments")
    @ApiResponse(responseCode = "404", description = "No review with that id",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public CommentPage listForReview(
        @PathVariable final Long reviewId,
        @Valid @ParameterObject final PageQuery page) {
        return commentService.listForReview(reviewId, page);
    }

    /** Returns a page of replies to a comment. */
    @GetMapping("/api/v1/comments/{commentId}/replies")
    @Operation(summary = "List a comment's replies")
    @ApiResponse(responseCode = "200", description = "A page of replies")
    public CommentPage listReplies(
        @PathVariable final Long commentId,
        @Valid @ParameterObject final PageQuery page) {
        return commentService.listReplies(commentId, page);
    }

    /** Removes the caller's own comment. */
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
