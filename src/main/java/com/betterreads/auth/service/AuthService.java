package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;

/**
 * Authentication operations: register a new account, log an existing one in, look up the
 * currently authenticated user by id, exchange a refresh token for a new access + refresh
 * token pair, and revoke a refresh token.
 */
public interface AuthService {

    /**
     * Creates a new user account and returns a fresh access + refresh token pair. Rejects
     * duplicate username or email with a {@link com.betterreads.common.exception.BusinessRuleException}.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates by username or email and returns a fresh access + refresh token pair. Throws
     * {@link org.springframework.security.authentication.BadCredentialsException} when the user
     * is unknown or the password doesn't match.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Loads the user profile for the given id. Throws
     * {@link com.betterreads.common.exception.ResourceNotFoundException} when the user is gone.
     */
    UserResponse currentUser(long userId);

    /**
     * Exchanges a refresh token for a new access + refresh token pair, rotating the refresh
     * token. Throws
     * {@link org.springframework.security.authentication.BadCredentialsException} when the
     * token is unknown, expired, revoked, or belongs to a deleted user.
     */
    AuthResponse refresh(String refreshToken);

    /**
     * Revokes a refresh token. Idempotent — unknown or already-revoked tokens are silently
     * accepted to avoid leaking which tokens existed.
     */
    void logout(String refreshToken);
}
