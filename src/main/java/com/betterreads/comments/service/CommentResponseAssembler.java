package com.betterreads.comments.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.comments.dto.CommentPage;
import com.betterreads.comments.dto.CommentResponse;
import com.betterreads.comments.entity.Comment;
import com.betterreads.comments.mapper.CommentMapper;
import com.betterreads.comments.repository.CommentRepository;
import com.betterreads.comments.repository.ReplyCount;
import com.betterreads.common.dto.PageQuery;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Turns a loaded page of comments into a {@link CommentPage}, resolving each comment's author
 * username and direct-reply count in one batch query each to avoid an N+1 per row.
 */
@Component
public class CommentResponseAssembler {

    private final UserRepository users;

    private final CommentRepository comments;

    private final CommentMapper mapper;

    public CommentResponseAssembler(
        final UserRepository users, final CommentRepository comments, final CommentMapper mapper) {
        this.users = users;
        this.comments = comments;
        this.mapper = mapper;
    }

    /**
     * Assembles the page.
     *
     * <p>{@code withReplyCounts} false skips the reply-count query: a reply has none under the
     * one-level cap, so the query would always return zero.
     */
    public CommentPage assemble(
        final Page<Comment> found, final PageQuery page, final boolean withReplyCounts) {
        final Map<Long, String> authors = authorsFor(found.getContent());
        final Map<Long, Long> replyCounts = withReplyCounts
            ? replyCountsFor(found.getContent())
            : Map.of();
        return mapper.toPage(found, authors, replyCounts, page.getOffset(), page.getLimit());
    }

    /** Builds the response for a freshly created comment, resolving its author's username. */
    public CommentResponse assembleOne(final Comment comment) {
        final String author = users.findById(comment.getUserId())
            .map(User::getUsername)
            .orElseThrow(() -> new IllegalStateException(
                "comment author has no app_user row userId=" + comment.getUserId()));
        return mapper.toResponse(comment, author);
    }

    private Map<Long, String> authorsFor(final List<Comment> page) {
        final List<Long> userIds = page.stream().map(Comment::getUserId).distinct().toList();
        return users.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getUserId, User::getUsername));
    }

    private Map<Long, Long> replyCountsFor(final List<Comment> page) {
        final List<Long> ids = page.stream().map(Comment::getCommentId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return comments.countRepliesForParents(ids).stream()
            .collect(Collectors.toMap(ReplyCount::parentCommentId, ReplyCount::count));
    }
}
