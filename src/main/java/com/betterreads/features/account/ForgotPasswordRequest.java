package com.betterreads.features.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ForgotPasswordRequest(
    @Schema(example = "john.doe@example.com")
    @NotBlank @Email @Size(max = 255) String email
) { }
