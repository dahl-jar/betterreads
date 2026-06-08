package com.betterreads.comments.dto;

import com.betterreads.common.dto.Paged;

import java.util.List;

/**
 * A page of comments.
 *
 * @param comments the comments on this page, in listing order
 * @param total the total number of comments across all pages
 * @param offset the zero-based offset of the first comment on this page
 * @param limit the page size
 */
public record CommentPage(
    List<CommentResponse> comments,
    long total,
    int offset,
    int limit
) implements Paged<CommentResponse> {

    public CommentPage {
        comments = List.copyOf(comments);
    }

    @Override
    public List<CommentResponse> comments() {
        return List.copyOf(comments);
    }

    @Override
    public List<CommentResponse> items() {
        return comments();
    }
}
