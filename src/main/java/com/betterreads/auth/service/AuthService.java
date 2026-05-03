package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;

/**
 * Authentication operations: register, log in, look up the current user, rotate a refresh
 * token, and revoke one.
 */
public interface AuthService {

    /**
     * Creates a new user account and returns a fresh access and refresh token pair.
     *
     * @throws com.betterreads.common.exception.BusinessRuleException duplicate username or email
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates the credentials and returns a fresh access and refresh token pair. The
     * identifier is matched against username first, then email.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException unknown user
     *     or wrong password
     */
    AuthResponse login(LoginRequest request);

    /**
     * Returns the profile of the user with the given id.
     *
     * @throws com.betterreads.common.exception.ResourceNotFoundException user no longer exists
     */
    UserResponse currentUser(long userId);

    /**
     * Exchanges the presented refresh token for a fresh access and refresh token pair, rotating
     * the old refresh token.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException token is
     *     unknown, expired, revoked, or its user is gone
     */
    AuthResponse refresh(String refreshToken);

    /**
     * Revokes the presented refresh token.
     *
     * <p>Idempotent: unknown or already-revoked tokens are silently accepted so callers cannot
     * probe which tokens exist.
     */
    void logout(String refreshToken);
}
