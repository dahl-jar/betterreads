package com.betterreads.auth.service;

import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;

/** Authentication operations. */
public interface AuthService {

    /**
     * Creates a new user account and returns the token pair.
     *
     * @throws com.betterreads.common.exception.BusinessRuleException duplicate username or email
     */
    TokenPair register(RegisterRequest request);

    /**
     * Authenticates the credentials and returns the token pair. The identifier is matched
     * against username first, then email.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException unknown user
     *     or wrong password
     */
    TokenPair login(LoginRequest request);

    /**
     * Returns the profile of the user with the given id.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException the user
     *     no longer exists; the endpoint maps this to {@code 401} so a stale token after
     *     self-delete is treated as session invalidation, not a missing resource
     */
    UserResponse currentUser(long userId);

    /**
     * Rotates the refresh token and returns a fresh token pair.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException token is
     *     unknown, expired, revoked, or its user is gone
     */
    TokenPair refresh(String refreshToken);

    /** Revokes the refresh token. Idempotent. */
    void logout(String refreshToken);
}
