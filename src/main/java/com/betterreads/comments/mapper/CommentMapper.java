package com.betterreads.comments.mapper;

import java.util.List;
import java.util.Map;

import com.betterreads.comments.dto.CommentPage;
import com.betterreads.comments.dto.CommentResponse;
import com.betterreads.comments.entity.Comment;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Builds comment responses, resolving each comment's author and reply count from batch lookups. */
@Component
public class CommentMapper {

    private static final String UNKNOWN_AUTHOR = "unknown";

    /** Builds the page response, defaulting a comment's author and reply count when absent from the lookups. */
    public CommentPage toPage(final Page<Comment> page, final Map<Long, String> authorsByUserId,
        final Map<Long, Long> replyCounts, final int offset, final int limit) {
        final List<CommentResponse> comments = page.getContent().stream()
            .map(comment -> toResponse(comment, authorsByUserId, replyCounts))
            .toList();
        return new CommentPage(comments, page.getTotalElements(), offset, limit);
    }

    /** Builds a single freshly created comment's response. A new comment has no replies. */
    public CommentResponse toResponse(final Comment comment, final String author) {
        return new CommentResponse(
            comment.getCommentId(), comment.getBody(), author,
            comment.getCreatedAt().toLocalDate(), 0L);
    }

    private CommentResponse toResponse(final Comment comment,
        final Map<Long, String> authorsByUserId, final Map<Long, Long> replyCounts) {
        return new CommentResponse(
            comment.getCommentId(),
            comment.getBody(),
            authorsByUserId.getOrDefault(comment.getUserId(), UNKNOWN_AUTHOR),
            comment.getCreatedAt().toLocalDate(),
            replyCounts.getOrDefault(comment.getCommentId(), 0L));
    }
}
