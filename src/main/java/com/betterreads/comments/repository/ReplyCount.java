package com.betterreads.comments.repository;

/**
 * The number of replies under one parent comment.
 *
 * @param parentCommentId the parent comment id
 * @param count the number of direct replies
 */
public record ReplyCount(Long parentCommentId, long count) {
}
