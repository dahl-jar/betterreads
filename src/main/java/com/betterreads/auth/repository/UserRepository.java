package com.betterreads.auth.repository;

import com.betterreads.auth.entity.User;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Persistence access for {@link User}. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Returns the user whose username matches case-insensitively, or empty if none does. */
    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsername(@Param("username") String username);

    Optional<User> findByEmail(String email);

    /** Returns true if a user already holds the username, compared case-insensitively. */
    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    boolean existsByEmail(String email);

    /**
     * Returns the user matching {@code email} under a row-level write lock.
     *
     * <p>Every flow that mutates an {@code email_token} for a user takes this lock first, so
     * the lock order is user-then-token across the codebase.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    /** Returns the user with the given id under a row-level write lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") long userId);
}
