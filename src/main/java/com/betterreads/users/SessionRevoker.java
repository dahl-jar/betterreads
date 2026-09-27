package com.betterreads.users;

// PMD.ImplicitFunctionalInterface: a Spring service contract implemented by one @Component.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface SessionRevoker {

    void revokeAllInCurrentTransaction(long userId);
}
