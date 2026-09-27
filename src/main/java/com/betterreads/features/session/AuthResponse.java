package com.betterreads.features.session;

import com.betterreads.users.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** Response body for register, login and refresh. The refresh token goes in the {@code br_refresh} cookie. */
record AuthResponse(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzMwMDAwMDAwfQ.signature",
        description = "Short-lived access JWT")
    String accessToken,

    UserResponse user
) { }
