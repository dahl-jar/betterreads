package com.betterreads.users;

import com.betterreads.crypto.PasswordByteLimit;

import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserPasswords {

    private final PasswordEncoder passwordEncoder;

    public UserPasswords(final PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void setPassword(final User user, final String rawPassword) {
        PasswordByteLimit.check(rawPassword);
        user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(rawPassword)));
        user.bumpCredentialVersion();
    }

    public boolean matches(final User user, final String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }
}
