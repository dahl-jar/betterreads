package com.betterreads.comments.service;

import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.comments.dto.CommentPage;
import com.betterreads.comments.dto.CommentResponse;
import com.betterreads.comments.dto.CreateCommentRequest;
import com.betterreads.comments.entity.Comment;
import com.betterreads.comments.entity.CommentTarget;
import com.betterreads.comments.repository.CommentRepository;
import com.betterreads.common.dto.PageQuery;
import com.betterreads.common.exception.ForbiddenException;
import com.betterreads.common.exception.InvalidRequestException;
import com.betterreads.common.exception.ResourceNotFoundException;
import com.betterreads.reviews.repository.ReviewRepository;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link CommentService}, backed by {@link CommentRepository}. */
@Service
public class CommentServiceImpl implements CommentService {

    private static final String NO_REVIEW = "No review with id ";

    private final CommentRepository comments;

    private final BookRepository books;

    private final ReviewRepository reviews;

    private final CommentResponseAssembler assembler;

    public CommentServiceImpl(
        final CommentRepository comments,
        final BookRepository books,
        final ReviewRepository reviews,
        final CommentResponseAssembler assembler) {
        this.comments = comments;
        this.books = books;
        this.reviews = reviews;
        this.assembler = assembler;
    }

    @Override
    @Transactional
    public CommentResponse commentOnBook(
        final Long userId, final String bookKey, final CreateCommentRequest request) {
        return create(userId, CommentTarget.BOOK, requireBookId(bookKey), request);
    }

    @Override
    @Transactional
    public CommentResponse commentOnReview(
        final Long userId, final Long reviewId, final CreateCommentRequest request) {
        final Long lockedReviewId = reviews.findForUpdate(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException(NO_REVIEW + reviewId))
            .getReviewId();
        return create(userId, CommentTarget.REVIEW, lockedReviewId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage listForBook(final String bookKey, final PageQuery page) {
        final Page<Comment> found = comments.findTopLevelForTarget(
            CommentTarget.BOOK, requireBookId(bookKey), page.toPageable());
        return assembler.assemble(found, page, true);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage listForReview(final Long reviewId, final PageQuery page) {
        final Page<Comment> found = comments.findTopLevelForTarget(
            CommentTarget.REVIEW, requireReviewId(reviewId), page.toPageable());
        return assembler.assemble(found, page, true);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage listReplies(final Long commentId, final PageQuery page) {
        final Page<Comment> found = comments.findReplies(commentId, page.toPageable());
        return assembler.assemble(found, page, false);
    }

    @Override
    @Transactional
    public void remove(final Long userId, final Long commentId) {
        final Comment comment = comments.findById(commentId).orElse(null);
        if (comment == null) {
            return;
        }
        if (!comment.getUserId().equals(userId)) {
            throw new ForbiddenException("A comment can only be removed by its author");
        }
        comments.delete(comment);
    }

    /** Persists a comment, rejecting a reply whose parent is itself a reply or on a different target. */
    private CommentResponse create(final Long userId, final CommentTarget targetType,
        final Long targetId, final CreateCommentRequest request) {
        if (request.parentCommentId() != null) {
            assertReplyableParent(request.parentCommentId(), targetType, targetId);
        }
        final Comment saved = comments.save(new Comment(
            userId, targetType, targetId, request.parentCommentId(), request.body()));
        return assembler.assembleOne(saved);
    }

    private void assertReplyableParent(
        final Long parentId, final CommentTarget targetType, final Long targetId) {
        final Comment parent = comments.findForUpdate(parentId)
            .orElseThrow(() -> new ResourceNotFoundException("No comment with id " + parentId));
        if (parent.getParentCommentId() != null) {
            throw new InvalidRequestException("A reply cannot be replied to");
        }
        if (parent.getTargetType() != targetType || !parent.getTargetId().equals(targetId)) {
            throw new InvalidRequestException("The parent comment belongs to a different target");
        }
    }

    private Long requireBookId(final String bookKey) {
        return books.findByDedupKey(bookKey)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + bookKey))
            .getBookId();
    }

    private Long requireReviewId(final Long reviewId) {
        if (!reviews.existsById(reviewId)) {
            throw new ResourceNotFoundException(NO_REVIEW + reviewId);
        }
        return reviewId;
    }
}
