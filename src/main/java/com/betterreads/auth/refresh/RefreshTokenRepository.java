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
 * Persistence access for {@link RefreshToken}. The hash lookup is the hot path — every
 * refresh and logout call hits {@link #findByTokenHash} once.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Same lookup as {@link #findByTokenHash} but acquires a {@code SELECT ... FOR UPDATE}
     * row lock on the matched row. Used by the rotation path to prevent two concurrent
     * refresh requests with the same token from both passing the {@code revokedAt is null}
     * check and issuing duplicate successors.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUserId(long userId);
}
