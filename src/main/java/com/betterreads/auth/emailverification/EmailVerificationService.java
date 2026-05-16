package com.betterreads.auth.emailverification;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.common.crypto.HmacTokenHasher;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use email-verification tokens.
 *
 * <p>Only the HMAC-SHA256 hash is stored. The resend side returns {@code 204} for every
 * branch so the response cannot be used to enumerate accounts.
 *
 * <p>Verification is idempotent: the same token clicked twice succeeds silently. Password
 * reset rejects replays because reset is destructive; verification is not.
 *
 * <p>Lock order is user-first. The verify path looks up the token without a lock, locks the
 * user, then re-fetches the token under that lock so issue-vs-verify cannot deadlock.
 */
@Service
public class EmailVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int TOKEN_BYTES = 32;

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired verification token";

    private final UserRepository userRepository;

    private final EmailTokenRepository tokenRepository;

    private final HmacTokenHasher hasher;

    private final MailOutboxService mailOutbox;

    private final SecureRandom random;

    public EmailVerificationService(
        final UserRepository userRepository,
        final EmailTokenRepository tokenRepository,
        final HmacTokenHasher hasher,
        final MailOutboxService mailOutbox
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.hasher = hasher;
        this.mailOutbox = mailOutbox;
        this.random = new SecureRandom();
    }

    /**
     * Issues a verification token and enqueues the verification email. Older tokens for the
     * user are consumed so only the latest link works.
     *
     * <p>Requires the user row to be either brand-new in this transaction or locked
     * {@code FOR UPDATE}. Without that serialization, two concurrent calls could both insert
     * an active token and violate the partial unique index.
     */
    @Transactional
    public void issueVerification(final long userId, final String recipient) {
        consumeOutstandingTokens(userId);
        final String plaintext = generatePlaintext();
        insertNewToken(userId, plaintext);
        mailOutbox.enqueueEmailVerification(recipient, plaintext);
        LOG.info("Issued verification token userId={}", userId);
    }

    /**
     * Resends the verification link for the matching account. Silent no-op for unknown
     * addresses and already-verified accounts so the response cannot be used to enumerate
     * registered or unverified emails.
     */
    @Transactional
    public void requestResend(final String email) {
        final String normalized = normalizeEmail(email);
        final Optional<User> userOpt = userRepository.findByEmailForUpdate(normalized);
        if (userOpt.isEmpty()) {
            LOG.info("Resend request for unknown email, no token issued");
            return;
        }
        final User user = userOpt.get();
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("Resend skipped: account already verified userId={}", user.getUserId());
            return;
        }
        issueVerification(user.getUserId(), user.getEmail());
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
        tokenRepository.findActive(userId, EmailToken.Purpose.EMAIL_VERIFICATION).forEach(t -> {
            t.setConsumedAt(now);
            tokenRepository.save(t);
        });
        tokenRepository.flush();
    }

    private void insertNewToken(final long userId, final String plaintext) {
        final EmailToken row = new EmailToken();
        row.setUserId(userId);
        row.setPurpose(EmailToken.Purpose.EMAIL_VERIFICATION);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokenRepository.saveAndFlush(row);
    }

    /**
     * Consumes the token and flips {@code email_verified_at} on the user. Throws
     * {@link InvalidRequestException} when the token is unknown, expired, superseded, or
     * refers to a deleted user.
     *
     * <p>A replay is only accepted when the user is already verified (same link clicked
     * twice). A consumed token whose user is still unverified means a later resend
     * superseded it, so this path rejects it like an unknown token.
     *
     * <p>Two-phase lookup (find token, lock user, re-fetch token) so two concurrent verifies
     * on the same token serialize on the user row instead of both passing
     * {@code consumed_at IS NULL}.
     */
    @Transactional
    public void verify(final String presentedToken) {
        final String hash = hasher.hash(presentedToken);
        final EmailToken initial = tokenRepository
            .findByHashAndPurpose(hash, EmailToken.Purpose.EMAIL_VERIFICATION)
            .orElseThrow(() -> {
                LOG.warn("Verification rejected: token unknown");
                return new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
            });
        final User user = userRepository.findByIdForUpdate(initial.getUserId())
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));
        final EmailToken row = tokenRepository
            .findByHashAndPurpose(hash, EmailToken.Purpose.EMAIL_VERIFICATION)
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));

        if (row.getConsumedAt() != null) {
            assertReplay(row, user);
            return;
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            LOG.warn("Verification rejected: token expired userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }

        final Instant now = Instant.now();
        user.setEmailVerifiedAt(now);
        userRepository.save(user);
        row.setConsumedAt(now);
        tokenRepository.save(row);
        LOG.info("Verified email userId={}", user.getUserId());
    }

    /**
     * Accepts a consumed token only when the user is already verified (replay), and rejects
     * the superseded case where a later resend exists.
     */
    private void assertReplay(final EmailToken row, final User user) {
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("Verification replay accepted, user already verified userId={}", row.getUserId());
            return;
        }
        LOG.warn("Verification rejected: token superseded by later resend userId={}", row.getUserId());
        throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
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
