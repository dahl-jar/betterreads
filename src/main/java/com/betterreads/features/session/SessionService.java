package com.betterreads.features.session;

import com.betterreads.users.UserResponse;

interface SessionService {

    void register(RegisterRequest request);

    SessionTokens login(LoginRequest request);

    UserResponse currentUser(long userId);

    SessionTokens refresh(String refreshToken);

    void logout(String refreshToken);
}
