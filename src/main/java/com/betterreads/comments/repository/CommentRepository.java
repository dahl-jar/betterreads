package com.betterreads.comments.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.betterreads.comments.entity.Comment;
import com.betterreads.comments.entity.CommentTarget;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Comment}, listed by target and by parent. */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Returns the comment locked for update, so a reply posted on it commits or fails before a
     * concurrent delete of the parent, turning the parent-FK violation into a controlled 404.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.commentId = :commentId")
    Optional<Comment> findForUpdate(@Param("commentId") Long commentId);

    /** Top-level comments on a target, newest first. Replies are excluded ({@code parent IS NULL}). */
    @Query("""
        SELECT c FROM Comment c
        WHERE c.targetType = :targetType AND c.targetId = :targetId AND c.parentCommentId IS NULL
        ORDER BY c.createdAt DESC, c.commentId DESC
        """)
    Page<Comment> findTopLevelForTarget(
        @Param("targetType") CommentTarget targetType, @Param("targetId") Long targetId,
        Pageable pageable);

    /** Replies to a comment, oldest first so a thread reads top to bottom. */
    @Query("SELECT c FROM Comment c WHERE c.parentCommentId = :parentId "
        + "ORDER BY c.createdAt ASC, c.commentId ASC")
    Page<Comment> findReplies(@Param("parentId") Long parentId, Pageable pageable);

    /** Returns the direct-reply count per parent; parents with no replies are absent from the result. */
    @Query("""
        SELECT new com.betterreads.comments.repository.ReplyCount(c.parentCommentId, COUNT(c))
        FROM Comment c
        WHERE c.parentCommentId IN :parentIds
        GROUP BY c.parentCommentId
        """)
    List<ReplyCount> countRepliesForParents(@Param("parentIds") Collection<Long> parentIds);
}
