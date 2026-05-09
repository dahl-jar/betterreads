package com.betterreads.auth.repository;

import com.betterreads.auth.entity.User;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link User}. {@code existsBy*} variants run as count queries to skip
 * loading the row when registration only needs to reject a duplicate.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Returns the user matching {@code email} under a row-level write lock. Used by every flow
     * that mutates an {@link com.betterreads.auth.token.EmailToken} for a user, so the issue
     * path (consume + insert + enqueue) and the consume path (verify / reset) cannot interleave
     * across requests for the same user. Project-wide rule: lock user first, then tokens.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    /**
     * Returns the user with the given id under a row-level write lock. Same rule as
     * {@link #findByEmailForUpdate(String)} for code paths that already hold the user id.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") long userId);
}
