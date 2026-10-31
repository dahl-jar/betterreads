package com.betterreads.features.session;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterRequest(
    @Schema(example = "john.doe", description = "3-50 chars: letters, digits, '.', '_', '-'")
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,

    @Schema(example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 254) String email,

    @Schema(example = "********", description = "8-72 bytes")
    @NotBlank @Size(min = 8, max = 72) String password
) { }
