package com.betterreads.auth.service;

import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;

/**
 * Authentication operations: register a new account, log an existing one in, look up the
 * currently authenticated user by id.
 */
public interface AuthService {

    /**
     * Creates a new user account and returns a signed JWT for the new user. Rejects duplicate
     * username or email with a {@link com.betterreads.common.exception.BusinessRuleException}.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates by username or email and returns a signed JWT. Throws
     * {@link org.springframework.security.authentication.BadCredentialsException} when the user
     * is unknown or the password doesn't match.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Loads the user profile for the given id. Throws
     * {@link com.betterreads.common.exception.ResourceNotFoundException} when the user is gone.
     */
    UserResponse currentUser(long userId);
}
