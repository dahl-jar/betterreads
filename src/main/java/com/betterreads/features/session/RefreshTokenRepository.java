package com.betterreads.features.session;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * two concurrent rotations of one token could both see it unrevoked and each issue a successor,
     * so the row is locked
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(long userId);

    @Query(value = """
        SELECT u.user_id FROM app_user u
        JOIN refresh_token rt ON rt.user_id = u.user_id
        WHERE rt.token_hash = :tokenHash
        FOR UPDATE OF u
        """, nativeQuery = true)
    Optional<Long> lockOwnerOfToken(@Param("tokenHash") String tokenHash);
}
