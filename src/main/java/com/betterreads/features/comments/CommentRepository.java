package com.betterreads.features.comments;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * a reply racing a delete of its parent would fail on the parent FK, so the parent is locked
     * and a deleted parent gives a 404
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.commentId = :commentId")
    Optional<Comment> findForUpdate(@Param("commentId") Long commentId);

    @Query("""
        SELECT c FROM Comment c
        WHERE c.targetType = :targetType AND c.targetId = :targetId AND c.parentCommentId IS NULL
        ORDER BY c.createdAt DESC, c.commentId DESC
        """)
    Page<Comment> findTopLevelForTarget(
        @Param("targetType") CommentTarget targetType, @Param("targetId") Long targetId,
        Pageable pageable);

    /** oldest first so a thread reads top to bottom */
    @Query("SELECT c FROM Comment c WHERE c.parentCommentId = :parentId "
        + "ORDER BY c.createdAt ASC, c.commentId ASC")
    Page<Comment> findReplies(@Param("parentId") Long parentId, Pageable pageable);

    /** parents with no replies are left out */
    @Query("""
        SELECT new com.betterreads.features.comments.ReplyCount(c.parentCommentId, COUNT(c))
        FROM Comment c
        WHERE c.parentCommentId IN :parentIds
        GROUP BY c.parentCommentId
        """)
    List<ReplyCount> countRepliesForParents(@Param("parentIds") Collection<Long> parentIds);
}
