package com.betterreads.features.session;

import com.betterreads.users.UserResponse;

/** Authentication operations. */
interface SessionService {

    /** @throws com.betterreads.errors.BusinessRuleException duplicate username or email */
    void register(RegisterRequest request);

    /**
     * The identifier is matched against username first, then email.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException unknown user
     *     or wrong password
     * @throws com.betterreads.errors.ForbiddenException the email is not verified
     */
    SessionTokens login(LoginRequest request);

    /**
     * @throws org.springframework.security.authentication.BadCredentialsException the user is
     *     gone, so a token that outlived a self-delete gets a 401
     */
    UserResponse currentUser(long userId);

    /**
     * @throws org.springframework.security.authentication.BadCredentialsException token is
     *     unknown, expired, revoked, or its user is gone
     */
    SessionTokens refresh(String refreshToken);

    void logout(String refreshToken);
}
