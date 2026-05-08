package com.betterreads.auth.emailverification;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link EmailVerificationToken}.
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * {@code SELECT ... FOR UPDATE} variant used by the verify path. Without the row lock, two
     * concurrent verify calls with the same valid token could both read {@code consumed_at IS NULL}
     * and each flip the user's verified timestamp. The lock serializes them so the second
     * transaction observes the row already consumed and exits idempotently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<EmailVerificationToken> findAllByUserIdAndConsumedAtIsNull(long userId);
}
