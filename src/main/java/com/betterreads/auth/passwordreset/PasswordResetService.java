package com.betterreads.auth.passwordreset;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.refresh.RefreshTokenChainRevoker;
import com.betterreads.auth.repository.UserRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use password-reset tokens. Plaintext is 256-bit random and never
 * stored; only the HMAC-SHA256 hash sits in the DB. A successful reset revokes every refresh
 * token for the user so other devices are kicked off.
 *
 * <p>The {@code requestReset} side does not reveal whether the email exists: the controller
 * returns {@code 204} for both branches, and the mailer is only invoked when an account
 * actually matches.
 */
@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_BYTES = 32;

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired reset token";

    private final UserRepository userRepository;

    private final PasswordResetTokenRepository tokenRepository;

    private final PasswordResetTokenHasher hasher;

    private final MailOutboxService mailOutbox;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenChainRevoker refreshTokenChainRevoker;

    private final SecureRandom random;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PasswordResetService(
        final UserRepository userRepository,
        final PasswordResetTokenRepository tokenRepository,
        final PasswordResetTokenHasher hasher,
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
     * Issues a reset token for the account matching {@code email} and hands the plaintext to
     * the configured mailer. Returns silently when no account matches so the caller cannot
     * distinguish a registered email from an unknown one.
     *
     * <p>Not annotated {@code @Transactional}: the consume-then-insert sequence is not required
     * to be atomic, and a unique-constraint violation on the insert would otherwise poison the
     * surrounding transaction with a rollback-only flag.
     */
    public void requestReset(final String email) {
        final String normalized = normalizeEmail(email);
        final Optional<User> userOpt = userRepository.findByEmail(normalized);
        if (userOpt.isEmpty()) {
            LOG.info("auth.password-reset.request.unknown-email");
            return;
        }
        final User user = userOpt.get();
        consumeOutstandingTokens(user.getUserId());
        final String plaintext = generatePlaintext();
        final boolean issued = tryInsertNewToken(user.getUserId(), plaintext);
        if (!issued) {
            LOG.info("auth.password-reset.request.race-lost userId={}", user.getUserId());
            return;
        }
        mailOutbox.enqueuePasswordReset(user.getEmail(), plaintext);
        LOG.info("auth.password-reset.request.issued userId={}", user.getUserId());
    }

    private void consumeOutstandingTokens(final long userId) {
        final Instant now = Instant.now();
        tokenRepository.findAllByUserIdAndConsumedAtIsNull(userId).forEach(t -> {
            t.setConsumedAt(now);
            tokenRepository.save(t);
        });
    }

    private boolean tryInsertNewToken(final long userId, final String plaintext) {
        final PasswordResetToken row = new PasswordResetToken();
        row.setUserId(userId);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        try {
            tokenRepository.saveAndFlush(row);
            return true;
        } catch (final DataIntegrityViolationException ex) {
            return false;
        }
    }

    /**
     * Consumes the presented reset token, replaces the user's password, and revokes every
     * refresh token belonging to the user. Throws {@link InvalidRequestException} when the token
     * is unknown, expired, or already consumed.
     *
     * <p>The same exception carries the same message for every failure branch so a caller
     * cannot distinguish "wrong token" from "expired" from "already used."
     */
    @Transactional
    public void resetPassword(final String presentedToken, final String newPassword) {
        final String hash = hasher.hash(presentedToken);
        final Optional<PasswordResetToken> rowOpt = tokenRepository.findByTokenHashForUpdate(hash);
        if (rowOpt.isEmpty()) {
            LOG.warn("auth.password-reset.consume.unknown-token");
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }
        final PasswordResetToken row = rowOpt.get();
        if (row.getConsumedAt() != null) {
            LOG.warn("auth.password-reset.consume.already-consumed userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            LOG.warn("auth.password-reset.consume.expired userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }

        final User user = userRepository.findById(row.getUserId())
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        row.setConsumedAt(Instant.now());
        tokenRepository.save(row);

        refreshTokenChainRevoker.revokeAllActiveForUserInTransaction(user.getUserId());
        LOG.info("auth.password-reset.consume.success userId={}", user.getUserId());
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
