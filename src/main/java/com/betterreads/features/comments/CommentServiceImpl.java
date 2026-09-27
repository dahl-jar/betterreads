package com.betterreads.features.comments;

import com.betterreads.bookaccess.BookIdLookup;
import com.betterreads.errors.ForbiddenException;
import com.betterreads.errors.InvalidRequestException;
import com.betterreads.errors.ResourceNotFoundException;
import com.betterreads.ratings.ReviewLookup;
import com.betterreads.web.PageQuery;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CommentServiceImpl implements CommentService {

    private final CommentRepository comments;

    private final BookIdLookup bookIds;

    private final ReviewLookup reviews;

    private final CommentResponseAssembler assembler;

    public CommentServiceImpl(
        final CommentRepository comments,
        final BookIdLookup bookIds,
        final ReviewLookup reviews,
        final CommentResponseAssembler assembler) {
        this.comments = comments;
        this.bookIds = bookIds;
        this.reviews = reviews;
        this.assembler = assembler;
    }

    @Override
    @Transactional
    public CommentResponse commentOnBook(
        final Long userId, final String bookKey, final CreateCommentRequest request) {
        return create(userId, CommentTarget.BOOK, bookIds.requireBookId(bookKey), request);
    }

    @Override
    @Transactional
    public CommentResponse commentOnReview(
        final Long userId, final Long reviewId, final CreateCommentRequest request) {
        return create(userId, CommentTarget.REVIEW, reviews.lockAndGetId(reviewId), request);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage listForBook(final String bookKey, final PageQuery page) {
        final Page<Comment> found = comments.findTopLevelForTarget(
            CommentTarget.BOOK, bookIds.requireBookId(bookKey), page.toPageable());
        return assembler.assemble(found, page);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage listForReview(final Long reviewId, final PageQuery page) {
        final Page<Comment> found = comments.findTopLevelForTarget(
            CommentTarget.REVIEW, reviews.requireReviewId(reviewId), page.toPageable());
        return assembler.assemble(found, page);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage listReplies(final Long commentId, final PageQuery page) {
        final Page<Comment> found = comments.findReplies(commentId, page.toPageable());
        return assembler.assembleReplies(found, page);
    }

    @Override
    @Transactional
    public void remove(final Long userId, final Long commentId) {
        comments.findById(commentId).ifPresent(comment -> {
            if (!comment.getUserId().equals(userId)) {
                throw new ForbiddenException("A comment can only be removed by its author");
            }
            comments.delete(comment);
        });
    }

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
}
