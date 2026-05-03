package com.betterreads.auth.service;

import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;

/**
 * Authentication operations: register, log in, look up the current user, rotate a refresh
 * token, and revoke one.
 */
public interface AuthService {

    /**
     * Creates a new user account and returns a fresh access token, response body, and refresh
     * token plaintext. The controller writes the refresh token to the {@code br_refresh} cookie
     * and serializes the body as JSON.
     *
     * @throws com.betterreads.common.exception.BusinessRuleException duplicate username or email
     */
    TokenPair register(RegisterRequest request);

    /**
     * Authenticates the credentials and returns a fresh token pair. The identifier is matched
     * against username first, then email.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException unknown user
     *     or wrong password
     */
    TokenPair login(LoginRequest request);

    /**
     * Returns the profile of the user with the given id.
     *
     * @throws com.betterreads.common.exception.ResourceNotFoundException user no longer exists
     */
    UserResponse currentUser(long userId);

    /**
     * Exchanges the presented refresh token for a fresh token pair, rotating the old refresh
     * token.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException token is
     *     unknown, expired, revoked, or its user is gone
     */
    TokenPair refresh(String refreshToken);

    /**
     * Revokes the presented refresh token.
     *
     * <p>Idempotent: unknown or already-revoked tokens are silently accepted so callers cannot
     * probe which tokens exist.
     */
    void logout(String refreshToken);
}
