package com.betterreads.auth.passwordreset;

import com.betterreads.auth.Emails;
import com.betterreads.auth.entity.User;
import com.betterreads.auth.refresh.RefreshTokenChainRevoker;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenIssuer;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.common.crypto.HmacTokenHasher;
import com.betterreads.common.crypto.PasswordByteLimit;
import com.betterreads.common.exception.InvalidRequestException;
import com.betterreads.mail.outbox.MailOutboxService;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use password-reset tokens.
 *
 * <p>Only the HMAC-SHA256 hash is stored. A successful reset revokes every refresh token for the
 * user. The request side returns {@code 204} for both known and unknown emails; the response does
 * not reveal whether an account exists.
 */
@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired reset token";

    private final UserRepository userRepository;

    private final EmailTokenRepository tokenRepository;

    private final EmailTokenIssuer tokenIssuer;

    private final HmacTokenHasher hasher;

    private final MailOutboxService mailOutbox;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenChainRevoker refreshTokenChainRevoker;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PasswordResetService(
        final UserRepository userRepository,
        final EmailTokenRepository tokenRepository,
        final EmailTokenIssuer tokenIssuer,
        final HmacTokenHasher hasher,
        final MailOutboxService mailOutbox,
        final PasswordEncoder passwordEncoder,
        final RefreshTokenChainRevoker refreshTokenChainRevoker
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenIssuer = tokenIssuer;
        this.hasher = hasher;
        this.mailOutbox = mailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenChainRevoker = refreshTokenChainRevoker;
    }

    /**
     * Issues a reset token for the matching account and enqueues the email. Silent no-op when
     * no account matches, so the response does not reveal whether the email is registered.
     *
     * <p>Locks the user row for the whole consume-insert-enqueue so two concurrent resets for
     * the same user serialize.
     */
    @Transactional
    public void requestReset(final String email) {
        final String normalized = Emails.normalize(email);
        final Optional<User> userOpt = userRepository.findByEmailForUpdate(normalized);
        if (userOpt.isEmpty()) {
            LOG.info("Password-reset request for unknown email, no token issued");
            return;
        }
        final User user = userOpt.get();
        final String plaintext =
            tokenIssuer.issue(user.getUserId(), EmailToken.Purpose.PASSWORD_RESET, TOKEN_LIFETIME);
        mailOutbox.enqueuePasswordReset(user.getEmail(), plaintext);
        LOG.info("Issued password-reset token userId={}", user.getUserId());
    }

    /**
     * Consumes the token, replaces the password, and revokes every refresh token for the user.
     * Throws {@link InvalidRequestException} when the token is unknown, expired, or consumed.
     *
     * <p>Every failure branch throws the same message so the response cannot be used to tell
     * "wrong token" from "expired" from "already used."
     *
     * <p>Two-phase lookup (find token, lock user, re-fetch token under lock) so two concurrent
     * consumes serialize on the user row instead of both passing the {@code consumed_at IS NULL}
     * check.
     */
    @Transactional
    public void resetPassword(final String presentedToken, final String newPassword) {
        PasswordByteLimit.check(newPassword);
        final String hash = hasher.hash(presentedToken);
        final EmailToken initial = tokenRepository
            .findByHashAndPurpose(hash, EmailToken.Purpose.PASSWORD_RESET)
            .orElseThrow(() -> {
                LOG.warn("Password-reset consume rejected: token unknown");
                return new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
            });
        final User user = userRepository.findByIdForUpdate(initial.getUserId())
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));
        final EmailToken row = tokenRepository
            .findByHashAndPurpose(hash, EmailToken.Purpose.PASSWORD_RESET)
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));

        if (row.getConsumedAt() != null) {
            LOG.warn("Password-reset consume rejected: token already used userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            LOG.warn("Password-reset consume rejected: token expired userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }

        user.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(newPassword)));
        userRepository.save(user);

        row.setConsumedAt(Instant.now());
        tokenRepository.save(row);

        refreshTokenChainRevoker.revokeAllInCurrentTransaction(user.getUserId());
        LOG.info("Password reset, all refresh tokens revoked userId={}", user.getUserId());
    }

}
