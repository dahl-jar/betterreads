package com.betterreads.features.comments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** parentCommentId is null for a top-level comment */
record CreateCommentRequest(
    @NotBlank @Size(max = 5000) String body,
    @Nullable Long parentCommentId
) {
}
