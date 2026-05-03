package com.betterreads.auth.controller;

import com.betterreads.auth.dto.AuthResponse;
import com.betterreads.auth.dto.LoginRequest;
import com.betterreads.auth.dto.RefreshRequest;
import com.betterreads.auth.dto.RegisterRequest;
import com.betterreads.auth.dto.UserResponse;
import com.betterreads.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. {@code register} and {@code login} are public; {@code me} requires
 * a valid JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register, log in, and look up the current user")
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    /**
     * Creates a new user and returns a signed JWT.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody final RegisterRequest request) {
        final AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates an existing user by username or email and returns a signed JWT.
     */
    @PostMapping("/login")
    @Operation(summary = "Log in with username or email")
    public AuthResponse login(@Valid @RequestBody final LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Returns the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    public UserResponse me(@AuthenticationPrincipal final Long userId) {
        return authService.currentUser(userId);
    }

    /**
     * Exchanges a refresh token for a new access + refresh token pair, rotating the refresh
     * token. Public endpoint authenticated solely by the refresh token in the request body.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate access and refresh tokens")
    public AuthResponse refresh(@Valid @RequestBody final RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /**
     * Revokes the given refresh token. Idempotent: returns 204 whether or not the token was
     * already revoked. Does not require an access token.
     */
    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody final RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
