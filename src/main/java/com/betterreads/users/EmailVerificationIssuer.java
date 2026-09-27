package com.betterreads.users;

// PMD.ImplicitFunctionalInterface: a Spring service contract implemented by one @Component.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface EmailVerificationIssuer {

    void issueVerification(long userId, String recipient);
}
