package com.betterreads.comments.dto;

import java.time.LocalDate;

/**
 * A comment returned by the comment endpoints.
 *
 * @param id the comment id
 * @param body the comment text
 * @param author the commenter's username
 * @param createdAt the date the comment was posted
 * @param replyCount the number of direct replies
 */
public record CommentResponse(
    long id,
    String body,
    String author,
    LocalDate createdAt,
    long replyCount
) {
}
