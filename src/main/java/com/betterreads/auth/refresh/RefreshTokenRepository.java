package com.betterreads.auth.refresh;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link RefreshToken}. Hash lookup is the hot path: every refresh and
 * logout call hits it once.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * {@code SELECT ... FOR UPDATE} variant used by the rotation path. Without the row lock,
     * two concurrent rotations with the same token could both pass the {@code revokedAt is null}
     * check and each issue a successor.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUserId(long userId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);
}
