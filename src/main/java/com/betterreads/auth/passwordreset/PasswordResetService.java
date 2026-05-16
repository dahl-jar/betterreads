package com.betterreads.auth.passwordreset;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.refresh.RefreshTokenChainRevoker;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.common.crypto.HmacTokenHasher;
import com.betterreads.common.crypto.PasswordByteLimit;
import com.betterreads.common.exception.InvalidRequestException;

import com.betterreads.mail.outbox.MailOutboxService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use password-reset tokens.
 *
 * <p>Only the HMAC-SHA256 hash is stored. A successful reset revokes every refresh token for
 * the user so other devices are signed out. The request side returns {@code 204} for both
 * known and unknown emails so the response cannot be used to enumerate accounts.
 */
@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_BYTES = 32;

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired reset token";

    private final UserRepository userRepository;

    private final EmailTokenRepository tokenRepository;

    private final HmacTokenHasher hasher;

    private final MailOutboxService mailOutbox;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenChainRevoker refreshTokenChainRevoker;

    private final SecureRandom random;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PasswordResetService(
        final UserRepository userRepository,
        final EmailTokenRepository tokenRepository,
        final HmacTokenHasher hasher,
        final MailOutboxService mailOutbox,
        final PasswordEncoder passwordEncoder,
        final RefreshTokenChainRevoker refreshTokenChainRevoker
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.hasher = hasher;
        this.mailOutbox = mailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenChainRevoker = refreshTokenChainRevoker;
        this.random = new SecureRandom();
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
        final String normalized = normalizeEmail(email);
        final Optional<User> userOpt = userRepository.findByEmailForUpdate(normalized);
        if (userOpt.isEmpty()) {
            LOG.info("Password-reset request for unknown email, no token issued");
            return;
        }
        final User user = userOpt.get();
        consumeOutstandingTokens(user.getUserId());
        final String plaintext = generatePlaintext();
        insertNewToken(user.getUserId(), plaintext);
        mailOutbox.enqueuePasswordReset(user.getEmail(), plaintext);
        LOG.info("Issued password-reset token userId={}", user.getUserId());
    }

    /**
     * Marks every active token for the user as consumed.
     *
     * <p>Flushes at the end because Hibernate's default action queue runs inserts before
     * updates, so the next insert would hit the partial unique index against the still-active
     * prior row.
     */
    private void consumeOutstandingTokens(final long userId) {
        final Instant now = Instant.now();
        tokenRepository.findActive(userId, EmailToken.Purpose.PASSWORD_RESET).forEach(t -> {
            t.setConsumedAt(now);
            tokenRepository.save(t);
        });
        tokenRepository.flush();
    }

    private void insertNewToken(final long userId, final String plaintext) {
        final EmailToken row = new EmailToken();
        row.setUserId(userId);
        row.setPurpose(EmailToken.Purpose.PASSWORD_RESET);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokenRepository.saveAndFlush(row);
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

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        row.setConsumedAt(Instant.now());
        tokenRepository.save(row);

        refreshTokenChainRevoker.revokeAllActiveForUserInTransaction(user.getUserId());
        LOG.info("Password reset, all refresh tokens revoked userId={}", user.getUserId());
    }

    private static String normalizeEmail(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generatePlaintext() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
