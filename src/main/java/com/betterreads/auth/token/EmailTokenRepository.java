package com.betterreads.auth.token;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link EmailToken}.
 */
@Repository
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    /**
     * Returns the row matching the presented hash and purpose, without locking. Used as the
     * first phase of the verify path: the caller looks up the token to discover its user_id,
     * then locks the user via {@link com.betterreads.auth.repository.UserRepository#findByIdForUpdate(long)},
     * then re-fetches under that lock so the row state is fresh. Project-wide lock rule is
     * user-first, so the token row itself is never directly locked.
     *
     * <p>Purpose is part of the lookup, not just a defensive check after the fact: a hash
     * collision across purposes is vanishingly unlikely in 256-bit HMAC space, but scoping
     * the query closes the angle entirely so a verification token cannot be presented to the
     * password-reset path or vice versa.
     */
    @Query("""
        SELECT t FROM EmailToken t
        WHERE t.tokenHash = :hash AND t.purpose = :purpose
        """)
    Optional<EmailToken> findByHashAndPurpose(
        @Param("hash") String hash,
        @Param("purpose") EmailToken.Purpose purpose
    );

    /**
     * Returns every active (unconsumed) row for the user under the given purpose. Used by the
     * issue path under the user lock to consume any prior outstanding token before inserting
     * a new one.
     */
    @Query("""
        SELECT t FROM EmailToken t
        WHERE t.userId = :userId AND t.purpose = :purpose AND t.consumedAt IS NULL
        """)
    List<EmailToken> findActive(
        @Param("userId") long userId,
        @Param("purpose") EmailToken.Purpose purpose
    );
}
