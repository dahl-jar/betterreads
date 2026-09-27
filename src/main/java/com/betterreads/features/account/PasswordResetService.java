package com.betterreads.features.account;

import com.betterreads.crypto.PasswordByteLimit;
import com.betterreads.errors.InvalidRequestException;
import com.betterreads.mailoutbox.MailOutboxService;
import com.betterreads.users.EmailNormalizer;
import com.betterreads.users.SessionRevoker;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use password-reset tokens.
 */
@Service
class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired reset token";

    private final UserRepository userRepository;

    private final EmailTokenIssuer tokenIssuer;

    private final EmailTokenRedeemer redeemer;

    private final MailOutboxService mailOutbox;

    private final PasswordEncoder passwordEncoder;

    private final SessionRevoker sessionRevoker;

    // PMD.ExcessiveParameterList: Spring injects one collaborator per step of the reset flow.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    PasswordResetService(
        final UserRepository userRepository,
        final EmailTokenIssuer tokenIssuer,
        final EmailTokenRedeemer redeemer,
        final MailOutboxService mailOutbox,
        final PasswordEncoder passwordEncoder,
        final SessionRevoker sessionRevoker
    ) {
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.redeemer = redeemer;
        this.mailOutbox = mailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevoker = sessionRevoker;
    }

    /**
     * Silent no-op when no account matches, so the response does not reveal whether the email
     * is registered.
     *
     * <p>Locks the user row for the whole consume-insert-enqueue so two concurrent resets for
     * the same user serialize.
     */
    @Transactional
    public void requestReset(final String email) {
        final String normalized = EmailNormalizer.normalize(email);
        userRepository.findByEmailForUpdate(normalized).ifPresentOrElse(
            this::issueReset,
            () -> LOG.info("Password-reset request for unknown email, no token issued"));
    }

    private void issueReset(final User user) {
        final String plaintext =
            tokenIssuer.issue(user.getUserId(), EmailToken.Purpose.PASSWORD_RESET, TOKEN_LIFETIME);
        mailOutbox.enqueuePasswordReset(user.getEmail(), plaintext);
        LOG.info("Issued password-reset token userId={}", user.getUserId());
    }

    /**
     * Revokes every refresh token for the user, so every device is signed out. Throws
     * {@code InvalidRequestException} when the token is unknown, expired, or consumed.
     *
     * <p>Every failure branch throws the same message so the response cannot be used to tell
     * "wrong token" from "expired" from "already used."
     */
    @Transactional
    public void resetPassword(final String presentedToken, final String newPassword) {
        PasswordByteLimit.check(newPassword);
        final EmailTokenRedeemer.Locked locked = redeemer.lockForRedeem(
            presentedToken, EmailToken.Purpose.PASSWORD_RESET, INVALID_OR_EXPIRED_TOKEN);
        final User user = locked.user();
        final EmailToken row = locked.token();

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

        redeemer.consume(row, Instant.now());

        sessionRevoker.revokeAllInCurrentTransaction(user.getUserId());
        LOG.info("Password reset, all refresh tokens revoked userId={}", user.getUserId());
    }
}
