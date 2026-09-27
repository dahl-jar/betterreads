package com.betterreads.features.session;

import com.betterreads.security.RefreshCookies;
import com.betterreads.users.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session endpoints. {@code me} needs an access JWT, the rest are public. */
@RestController
@RequestMapping(RefreshCookies.COOKIE_PATH)
@Tag(name = "Authentication", description = "Register, log in, and look up the current user")
class SessionController {

    private final SessionService sessionService;

    private final RefreshCookies refreshCookies;

    SessionController(final SessionService sessionService, final RefreshCookies refreshCookies) {
        this.sessionService = sessionService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Username or email already taken",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody final RegisterRequest request) {
        final SessionTokens tokens = sessionService.register(request);
        return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED), tokens);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username or email")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Rate limited",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody final LoginRequest request) {
        final SessionTokens tokens = sessionService.login(request);
        return withRefreshCookie(ResponseEntity.ok(), tokens);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user profile")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public UserResponse me(@AuthenticationPrincipal final Long userId) {
        return sessionService.currentUser(userId);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate access and refresh tokens")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "New access token, refresh cookie rotated")
    @ApiResponse(responseCode = "401", description = "Missing, expired, or already-rotated refresh token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = RefreshCookies.COOKIE_NAME, required = false) final String refreshToken
    ) {
        if (refreshToken == null) {
            throw new BadCredentialsException("Missing refresh token");
        }
        final SessionTokens tokens = sessionService.refresh(refreshToken);
        return withRefreshCookie(ResponseEntity.ok(), tokens);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Refresh cookie cleared")
    public ResponseEntity<Void> logout(
        @CookieValue(name = RefreshCookies.COOKIE_NAME, required = false) final String refreshToken
    ) {
        if (refreshToken != null) {
            sessionService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
            .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(
        final ResponseEntity.BodyBuilder builder, final SessionTokens tokens
    ) {
        return builder
            .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(tokens.refreshToken()).toString())
            .body(tokens.body());
    }
}
