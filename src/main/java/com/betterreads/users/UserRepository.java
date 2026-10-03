package com.betterreads.users;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u.userId FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<Long> findIdByUsername(@Param("username") String username);

    @Query("SELECT u.userId FROM User u WHERE u.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);

    /** Compares the username case-insensitively. */
    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    boolean existsByEmail(String email);

    /** Flows that change a user's {@code email_token} take this lock first, so locks go user then token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") long userId);
}
