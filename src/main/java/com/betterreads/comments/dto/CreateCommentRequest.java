package com.betterreads.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * Body of a create-comment request.
 *
 * @param body the comment text
 * @param parentCommentId the comment being replied to, null for a top-level comment
 */
public record CreateCommentRequest(
    @NotBlank @Size(max = 5000) String body,
    @Nullable Long parentCommentId
) {
}
