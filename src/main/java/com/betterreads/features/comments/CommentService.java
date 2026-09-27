package com.betterreads.features.comments;

import com.betterreads.web.PageQuery;

interface CommentService {

    CommentResponse commentOnBook(Long userId, String bookKey, CreateCommentRequest request);

    CommentResponse commentOnReview(Long userId, Long reviewId, CreateCommentRequest request);

    /** top-level comments only */
    CommentPage listForBook(String bookKey, PageQuery page);

    /** top-level comments only */
    CommentPage listForReview(Long reviewId, PageQuery page);

    CommentPage listReplies(Long commentId, PageQuery page);

    /** a missing comment is a no-op, someone else's comment is a 403 */
    void remove(Long userId, Long commentId);
}
