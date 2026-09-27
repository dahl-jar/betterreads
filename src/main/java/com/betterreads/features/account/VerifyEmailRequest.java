package com.betterreads.features.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record VerifyEmailRequest(
    @Schema(example = "8f2a3c4e5d6b7a8f2a3c4e5d6b7a8f2a", description = "Verification token from the email link")
    @NotBlank @Size(max = 128) String token
) { }
