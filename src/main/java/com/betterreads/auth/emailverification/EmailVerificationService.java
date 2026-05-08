package com.betterreads.auth.emailverification;

import com.betterreads.auth.entity.User;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use email-verification tokens. Plaintext is 256-bit random and
 * never stored; only the HMAC-SHA256 hash sits in the DB.
 *
 * <p>The {@code requestResend} side does not reveal whether the email exists or whether the
 * account is already verified: the controller returns {@code 204} for every branch, and the
 * mailer is only invoked when an unverified account actually matches.
 *
 * <p>Verification is idempotent: presenting the same token twice returns silently the second
 * time so a user who clicks the link from two devices, or whose mail client prefetches the link,
 * does not see an error. This is different from password reset (where replay is rejected),
 * because verification is non-destructive.
 *
 * <p>Lock-order rule: the verify path locks the token row first, then updates the user row.
 * Future code that mutates {@link User} alongside an email-verification token must follow the
 * same order to avoid deadlocks.
 */
@Service
public class EmailVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int TOKEN_BYTES = 32;

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired verification token";

    private final UserRepository userRepository;

    private final EmailVerificationTokenRepository tokenRepository;

    private final EmailVerificationTokenHasher hasher;

    private final MailOutboxService mailOutbox;

    private final SecureRandom random;

    public EmailVerificationService(
        final UserRepository userRepository,
        final EmailVerificationTokenRepository tokenRepository,
        final EmailVerificationTokenHasher hasher,
        final MailOutboxService mailOutbox
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.hasher = hasher;
        this.mailOutbox = mailOutbox;
        this.random = new SecureRandom();
    }

    /**
     * Issues a fresh verification token for the given user and enqueues the verification email.
     * Consumes any prior outstanding tokens for the user so only the latest link works.
     *
     * <p>Not annotated {@code @Transactional}: the consume-then-insert sequence is not required
     * to be atomic, and a unique-constraint violation on the insert would otherwise poison a
     * surrounding transaction with a rollback-only flag. Callers that need atomicity with their
     * own writes (registration) hold the outer {@code @Transactional} themselves; the JPA
     * operations here join that thread-bound session.
     *
     * <p>Loses the partial-unique-index race silently: if a concurrent caller already inserted
     * a fresh token for the same user, this returns without enqueueing a duplicate mail.
     */
    public void issueVerification(final long userId, final String recipient) {
        consumeOutstandingTokens(userId);
        final String plaintext = generatePlaintext();
        final boolean issued = tryInsertNewToken(userId, plaintext);
        if (!issued) {
            LOG.info("auth.email-verification.issue.race-lost userId={}", userId);
            return;
        }
        mailOutbox.enqueueEmailVerification(recipient, plaintext);
        LOG.info("auth.email-verification.issue.success userId={}", userId);
    }

    /**
     * Resends a verification link for the account matching {@code email}. Returns silently for
     * unknown addresses and for accounts that are already verified so the response cannot be
     * used to enumerate registered or unverified emails.
     */
    public void requestResend(final String email) {
        final String normalized = normalizeEmail(email);
        final Optional<User> userOpt = userRepository.findByEmail(normalized);
        if (userOpt.isEmpty()) {
            LOG.info("auth.email-verification.resend.unknown-email");
            return;
        }
        final User user = userOpt.get();
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("auth.email-verification.resend.already-verified userId={}", user.getUserId());
            return;
        }
        issueVerification(user.getUserId(), user.getEmail());
    }

    private void consumeOutstandingTokens(final long userId) {
        final Instant now = Instant.now();
        tokenRepository.findAllByUserIdAndConsumedAtIsNull(userId).forEach(t -> {
            t.setConsumedAt(now);
            tokenRepository.save(t);
        });
    }

    private boolean tryInsertNewToken(final long userId, final String plaintext) {
        final EmailVerificationToken row = new EmailVerificationToken();
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
     * Consumes the presented verification token and flips {@code email_verified_at} on the
     * matching user. Throws {@link InvalidRequestException} when the token is unknown, expired,
     * superseded by a later resend, or refers to a deleted user.
     *
     * <p>Idempotent replay returns silently only when the token is already consumed AND the
     * user is already verified (the same token clicked twice). A consumed token whose user is
     * still unverified means the token was superseded by a later resend, so this path returns
     * the same {@code invalid/expired} error as an unknown token; only the freshly issued
     * token can complete verification.
     *
     * <p>Locks the token row {@code FOR UPDATE} so concurrent verify calls with the same token
     * are serialized: the second transaction sees {@code consumed_at != null} and exits without
     * a second user write.
     */
    @Transactional
    public void verify(final String presentedToken) {
        final String hash = hasher.hash(presentedToken);
        final EmailVerificationToken row = tokenRepository.findByTokenHashForUpdate(hash)
            .orElseThrow(() -> {
                LOG.warn("auth.email-verification.verify.unknown-token");
                return new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
            });
        final User user = userRepository.findById(row.getUserId())
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));
        if (row.getConsumedAt() != null) {
            handleAlreadyConsumed(row, user);
            return;
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            LOG.warn("auth.email-verification.verify.expired userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }

        final Instant now = Instant.now();
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        }
        row.setConsumedAt(now);
        tokenRepository.save(row);
        LOG.info("auth.email-verification.verify.success userId={}", user.getUserId());
    }

    /**
     * Distinguishes idempotent replay (user is verified) from superseded-by-resend (user is
     * still unverified) on a token whose {@code consumed_at} is already set.
     */
    private void handleAlreadyConsumed(final EmailVerificationToken row, final User user) {
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("auth.email-verification.verify.replay userId={}", row.getUserId());
            return;
        }
        LOG.warn("auth.email-verification.verify.superseded userId={}", row.getUserId());
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
