package com.betterreads.features.session;

/** Only {@code body} goes out as JSON. The refresh token is kept apart so the controller can set it as a cookie. */
record SessionTokens(AuthResponse body, String refreshToken) { }
