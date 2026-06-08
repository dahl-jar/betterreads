package com.betterreads.comments.service;

import com.betterreads.comments.dto.CommentPage;
import com.betterreads.comments.dto.CommentResponse;
import com.betterreads.comments.dto.CreateCommentRequest;
import com.betterreads.common.dto.PageQuery;

/** Reads and writes comments on books and reviews. */
public interface CommentService {

    /** Posts a comment or reply on the book addressed by its public key. */
    CommentResponse commentOnBook(Long userId, String bookKey, CreateCommentRequest request);

    /** Posts a comment or reply on a review. */
    CommentResponse commentOnReview(Long userId, Long reviewId, CreateCommentRequest request);

    /** Returns a page of top-level comments on the book. */
    CommentPage listForBook(String bookKey, PageQuery page);

    /** Returns a page of top-level comments on the review. */
    CommentPage listForReview(Long reviewId, PageQuery page);

    /** Returns a page of replies to a comment. */
    CommentPage listReplies(Long commentId, PageQuery page);

    /** Removes the caller's own comment. */
    void remove(Long userId, Long commentId);
}
