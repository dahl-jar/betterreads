package com.betterreads.features.session;

import java.time.Instant;

record RefreshGrant(String plaintext, Instant expiresAt, boolean persistent) { }
