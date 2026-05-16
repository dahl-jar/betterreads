package com.betterreads.auth.refresh;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence access for {@link RefreshToken}. */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Hash lookup under a row-level write lock, used by the rotation path.
     *
     * <p>Without the lock, two concurrent rotations on the same token could both pass the
     * {@code revokedAt IS NULL} check and each issue a successor.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUserId(long userId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);
}
