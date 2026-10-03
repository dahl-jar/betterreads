package com.betterreads.security;

public record AccessToken(long userId, int credentialVersion) {
}
