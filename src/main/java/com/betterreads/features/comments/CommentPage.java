package com.betterreads.features.comments;

import com.betterreads.web.Paged;

import java.util.List;

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
