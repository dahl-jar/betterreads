package com.betterreads.features.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ChangePasswordRequest(
    @Schema(example = "********")
    @NotBlank @Size(max = 72) String currentPassword,

    @Schema(example = "********", description = "8-72 bytes")
    @NotBlank @Size(min = 8, max = 72) String newPassword
) { }
