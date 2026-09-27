package com.betterreads.features.comments;

import java.time.LocalDate;

public record CommentResponse(
    long id,
    String body,
    String author,
    LocalDate createdAt,
    long replyCount
) {
}
