package com.betterreads.features.session;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** Request body for {@code POST /api/v1/auth/login}. */
record LoginRequest(
    @Schema(example = "john.doe@example.com", description = "Username or email")
    @NotBlank String identifier,

    @Schema(example = "********", description = "Account password")
    @NotBlank String password,

    @Schema(example = "true",
        description = "Keeps the reader logged in for 30 days. Without it the login ends when the browser closes")
    @Nullable Boolean rememberMe
) { }
