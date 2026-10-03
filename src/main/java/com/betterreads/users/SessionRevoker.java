package com.betterreads.users;

import org.jspecify.annotations.Nullable;

public interface SessionRevoker {

    void revokeAllInCurrentTransaction(long userId);

    void revokeOthersInCurrentTransaction(long userId, @Nullable String keptRefreshToken);
}
