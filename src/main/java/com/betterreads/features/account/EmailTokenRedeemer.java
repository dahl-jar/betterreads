package com.betterreads.features.account;

import com.betterreads.crypto.HmacTokenHasher;
import com.betterreads.errors.InvalidRequestException;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class EmailTokenRedeemer {

    private static final Logger LOG = LoggerFactory.getLogger(EmailTokenRedeemer.class);

    private final UserRepository userRepository;

    private final EmailTokenRepository tokenRepository;

    private final HmacTokenHasher hasher;

    EmailTokenRedeemer(
        final UserRepository userRepository,
        final EmailTokenRepository tokenRepository,
        final HmacTokenHasher hasher
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.hasher = hasher;
    }

    /** concurrent redeems of one token serialize on the user row lock, so the token is re-read after taking it */
    Locked lockForRedeem(
        final String presentedToken,
        final EmailToken.Purpose purpose,
        final String invalidMessage
    ) {
        final String hash = hasher.hash(presentedToken);
        final EmailToken initial = tokenRepository
            .findByHashAndPurpose(hash, purpose)
            .orElseThrow(() -> {
                LOG.warn("Email token rejected: token unknown");
                return new InvalidRequestException(invalidMessage);
            });
        final User user = userRepository.findByIdForUpdate(initial.getUserId())
            .orElseThrow(() -> new InvalidRequestException(invalidMessage));
        final EmailToken token = tokenRepository
            .findByHashAndPurpose(hash, purpose)
            .orElseThrow(() -> new InvalidRequestException(invalidMessage));
        return new Locked(user, token);
    }

    void consume(final EmailToken token, final Instant now) {
        token.setConsumedAt(now);
        tokenRepository.save(token);
    }

    record Locked(User user, EmailToken token) {
    }
}
