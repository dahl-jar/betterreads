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

    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM app_user WHERE lower(username) = lower(:username))
        """, nativeQuery = true)
    boolean isUsernameTaken(@Param("username") String username);

    @Query(value = """
        SELECT EXISTS (SELECT 1 FROM app_user WHERE email = :email)
        """, nativeQuery = true)
    boolean isEmailTaken(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") long userId);
}
