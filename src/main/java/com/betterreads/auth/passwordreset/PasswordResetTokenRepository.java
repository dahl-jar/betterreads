package com.betterreads.auth.passwordreset;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link PasswordResetToken}.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * {@code SELECT ... FOR UPDATE} variant used by the consume path. Without the row lock,
     * two concurrent reset-password calls with the same valid token could both pass the
     * {@code consumedAt is null} check and each apply a password change.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetToken> findAllByUserIdAndConsumedAtIsNull(long userId);
}
