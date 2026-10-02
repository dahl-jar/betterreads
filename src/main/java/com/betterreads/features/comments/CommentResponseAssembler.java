package com.betterreads.features.comments;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.betterreads.users.UsernameLookup;
import com.betterreads.web.PageQuery;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** loads authors and reply counts with one query each, so a page is not an N+1 per comment */
@Component
class CommentResponseAssembler {

    private final UsernameLookup usernames;

    private final CommentRepository comments;

    public CommentResponseAssembler(final UsernameLookup usernames, final CommentRepository comments) {
        this.usernames = usernames;
        this.comments = comments;
    }

    public CommentPage assemble(final Page<Comment> found, final PageQuery page) {
        return toPage(found, page, replyCountsFor(found.getContent()));
    }

    /** replies can't have replies, so a page of replies skips the reply-count query */
    public CommentPage assembleReplies(final Page<Comment> found, final PageQuery page) {
        return toPage(found, page, Map.of());
    }

    public CommentResponse assembleOne(final Comment comment) {
        final String author = usernames.usernameOf(comment.getUserId())
            .orElseThrow(() -> new IllegalStateException(
                "comment author has no app_user row userId=" + comment.getUserId()));
        return toResponse(comment, author, 0L);
    }

    private CommentPage toPage(
        final Page<Comment> found, final PageQuery page, final Map<Long, Long> replyCounts) {
        final Map<Long, String> authors = authorsFor(found.getContent());
        final List<CommentResponse> responses = found.getContent().stream()
            .map(comment -> toResponse(comment,
                UsernameLookup.usernameIn(authors, comment.getUserId()),
                replyCounts.getOrDefault(comment.getCommentId(), 0L)))
            .toList();
        return new CommentPage(responses, found.getTotalElements(), page.getOffset(), page.getLimit());
    }

    private static CommentResponse toResponse(
        final Comment comment, final String author, final long replyCount) {
        return new CommentResponse(
            comment.getCommentId(), comment.getBody(), author,
            comment.getCreatedAt().toLocalDate(), replyCount);
    }

    private Map<Long, String> authorsFor(final List<Comment> rows) {
        final List<Long> userIds = rows.stream().map(Comment::getUserId).distinct().toList();
        return usernames.usernamesByIds(userIds);
    }

    private Map<Long, Long> replyCountsFor(final List<Comment> rows) {
        final List<Long> ids = rows.stream().map(Comment::getCommentId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return comments.countRepliesForParents(ids).stream()
            .collect(Collectors.toMap(ReplyCount::parentCommentId, ReplyCount::count));
    }
}
