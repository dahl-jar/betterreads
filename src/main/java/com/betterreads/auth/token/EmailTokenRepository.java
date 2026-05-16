package com.betterreads.auth.token;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence access for {@link EmailToken}. */
@Repository
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    /**
     * Returns the token matching the given hash and purpose, or empty if none matches.
     *
     * <p>Purpose is part of the lookup so a token issued for one feature cannot be presented
     * to another.
     */
    @Query("""
        SELECT t FROM EmailToken t
        WHERE t.tokenHash = :hash AND t.purpose = :purpose
        """)
    Optional<EmailToken> findByHashAndPurpose(
        @Param("hash") String hash,
        @Param("purpose") EmailToken.Purpose purpose
    );

    /** Returns every unconsumed token for the user under the given purpose. */
    @Query("""
        SELECT t FROM EmailToken t
        WHERE t.userId = :userId AND t.purpose = :purpose AND t.consumedAt IS NULL
        """)
    List<EmailToken> findActive(
        @Param("userId") long userId,
        @Param("purpose") EmailToken.Purpose purpose
    );
}
